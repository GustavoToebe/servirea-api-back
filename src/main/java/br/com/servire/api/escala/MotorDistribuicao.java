package br.com.servire.api.escala;

import br.com.servire.api.voluntario.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Algoritmo determinístico e limitado: restrições primeiro, menor carga e UUID no desempate. */
public final class MotorDistribuicao {
  private MotorDistribuicao() {}

  public record Regras(int maximoPorPessoa, int intervaloDias, boolean exigirResposta) {}

  public record Vaga(
      UUID id,
      UUID evento,
      LocalDate data,
      LocalTime hora,
      FuncaoEscala funcao,
      UUID atual,
      long versao) {}

  public record Positiva(LocalDate data, DayOfWeek dia, Periodo periodo) {}

  public record Negativa(LocalDate data, Periodo periodo) {}

  public record Pessoa(
      UUID id,
      String nome,
      Set<FuncaoEscala> funcoes,
      List<Positiva> positivas,
      List<Negativa> negativas,
      Set<YearMonth> respostas) {}

  public record Reserva(UUID pessoa, UUID evento, LocalDate data, LocalTime hora) {}

  public record Sugestao(UUID vagaId, UUID pessoaId, String nome, String explicacao) {}

  public record Conflito(
      UUID vagaId, String explicacao, Map<String, Integer> descartes, boolean alocacaoExistente) {}

  public record Resultado(List<Sugestao> sugestoes, List<Conflito> conflitos, boolean bloqueado) {}

  public static Resultado gerar(
      List<Vaga> vagas, List<Pessoa> pessoas, List<Reserva> outras, Regras regras) {
    var porPessoa = new HashMap<UUID, Pessoa>();
    pessoas.forEach(p -> porPessoa.put(p.id(), p));
    var reservas = new HashMap<UUID, List<Reserva>>();
    outras.forEach(r -> reservas.computeIfAbsent(r.pessoa(), x -> new ArrayList<>()).add(r));
    var cargas = new HashMap<UUID, Integer>();
    outras.forEach(r -> cargas.merge(r.pessoa(), 1, Integer::sum));
    for (var v : vagas)
      if (v.atual() != null) {
        reservas
            .computeIfAbsent(v.atual(), x -> new ArrayList<>())
            .add(new Reserva(v.atual(), v.evento(), v.data(), v.hora()));
        cargas.merge(v.atual(), 1, Integer::sum);
      }
    List<Conflito> conflitos = new ArrayList<>();
    List<Sugestao> sugestoes = new ArrayList<>();
    for (var v : vagas)
      if (v.atual() != null) {
        var p = porPessoa.get(v.atual());
        String motivo = p == null ? "INATIVO_OU_AUSENTE" : elegibilidade(p, v, regras);
        if (motivo == null && cargas.getOrDefault(p.id(), 0) > regras.maximoPorPessoa())
          motivo = "LIMITE_POR_PESSOA";
        if (motivo == null)
          motivo = colisao(v, reservas.getOrDefault(p.id(), List.of()), regras, true);
        if (motivo != null)
          conflitos.add(
              new Conflito(
                  v.id(),
                  "A alocação existente precisa de revisão: " + rotulo(motivo),
                  Map.of(motivo, 1),
                  true));
      }
    // Eventos com menor número de elegíveis recebem prioridade; preserva determinismo.
    var vazias =
        vagas.stream()
            .filter(v -> v.atual() == null)
            .sorted(
                Comparator.comparingLong(
                        (Vaga v) ->
                            pessoas.stream()
                                .filter(p -> elegibilidade(p, v, regras) == null)
                                .count())
                    .thenComparing(Vaga::data)
                    .thenComparing(Vaga::hora)
                    .thenComparing(Vaga::id))
            .toList();
    for (var v : vazias) {
      var descartes = new TreeMap<String, Integer>();
      List<Pessoa> aptas = new ArrayList<>();
      for (var p : pessoas) {
        String motivo = elegibilidade(p, v, regras);
        if (motivo == null && cargas.getOrDefault(p.id(), 0) >= regras.maximoPorPessoa())
          motivo = "LIMITE_POR_PESSOA";
        if (motivo == null)
          motivo = colisao(v, reservas.getOrDefault(p.id(), List.of()), regras, false);
        if (motivo == null) aptas.add(p);
        else descartes.merge(motivo, 1, Integer::sum);
      }
      if (aptas.isEmpty()) {
        conflitos.add(
            new Conflito(
                v.id(),
                "Nenhum candidato atende às regras. A vaga permanece vazia.",
                Map.copyOf(descartes),
                false));
        continue;
      }
      aptas.sort(
          Comparator.comparingInt((Pessoa p) -> cargas.getOrDefault(p.id(), 0))
              .thenComparing(Pessoa::id));
      var escolhida = aptas.getFirst();
      int carga = cargas.getOrDefault(escolhida.id(), 0);
      sugestoes.add(
          new Sugestao(
              v.id(),
              escolhida.id(),
              escolhida.nome(),
              "Função habilitada, disponibilidade válida e sem conflito; "
                  + carga
                  + " alocações consideradas antes desta sugestão. Menor carga entre os elegíveis;"
                  + " UUID desempata."));
      cargas.merge(escolhida.id(), 1, Integer::sum);
      reservas
          .computeIfAbsent(escolhida.id(), x -> new ArrayList<>())
          .add(new Reserva(escolhida.id(), v.evento(), v.data(), v.hora()));
    }
    return new Resultado(
        List.copyOf(sugestoes),
        List.copyOf(conflitos),
        conflitos.stream().anyMatch(Conflito::alocacaoExistente));
  }

  private static String elegibilidade(Pessoa p, Vaga v, Regras r) {
    if (!p.funcoes().contains(v.funcao())) return "FUNCAO_NAO_HABILITADA";
    var periodo = EscalaService.periodoDe(v.hora());
    if (p.negativas().stream()
        .anyMatch(
            n -> n.data().equals(v.data()) && (n.periodo() == null || n.periodo() == periodo)))
      return "INDISPONIVEL";
    if (!p.positivas().isEmpty()
        && p.positivas().stream()
            .noneMatch(
                n ->
                    n.periodo() == periodo
                        && (v.data().equals(n.data()) || v.data().getDayOfWeek() == n.dia())))
      return "FORA_DA_DISPONIBILIDADE";
    if (r.exigirResposta()
        && !p.respostas().contains(YearMonth.from(v.data()))
        && p.negativas().stream()
            .noneMatch(n -> YearMonth.from(n.data()).equals(YearMonth.from(v.data()))))
      return "RESPOSTA_PENDENTE";
    return null;
  }

  private static String colisao(Vaga v, List<Reserva> reservas, Regras r, boolean propria) {
    boolean ignorou = false;
    for (var outra : reservas) {
      if (propria
          && !ignorou
          && outra.evento().equals(v.evento())
          && outra.data().equals(v.data())
          && outra.hora().equals(v.hora())) {
        ignorou = true;
        continue;
      }
      if (outra.evento().equals(v.evento())
          || outra.data().equals(v.data()) && outra.hora().equals(v.hora()))
        return "HORARIO_OCUPADO";
      if (Math.abs(ChronoUnit.DAYS.between(outra.data(), v.data())) < r.intervaloDias())
        return "INTERVALO_INSUFICIENTE";
    }
    return null;
  }

  public static String rotulo(String motivo) {
    return switch (motivo) {
      case "FUNCAO_NAO_HABILITADA" -> "função não habilitada";
      case "INDISPONIVEL" -> "data/período indisponível";
      case "FORA_DA_DISPONIBILIDADE" -> "fora da disponibilidade cadastrada";
      case "RESPOSTA_PENDENTE" -> "disponibilidade ainda não respondida";
      case "LIMITE_POR_PESSOA" -> "limite de participação";
      case "HORARIO_OCUPADO" -> "outra vaga no mesmo evento/horário";
      case "INTERVALO_INSUFICIENTE" -> "intervalo entre participações";
      default -> "voluntário inativo ou ausente";
    };
  }
}
