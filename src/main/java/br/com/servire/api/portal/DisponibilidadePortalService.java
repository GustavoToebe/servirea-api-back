package br.com.servire.api.portal;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.escala.*;
import br.com.servire.api.integracao.FuncionalidadesPlano;
import br.com.servire.api.portal.dto.DisponibilidadeDtos.*;
import br.com.servire.api.voluntario.VoluntarioRepository;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Somente a pessoa explicitamente vinculada. Datas negativas alimentam a elegibilidade já existente. */
@Service
public class DisponibilidadePortalService {
  private final PortalService portal;
  private final FuncionalidadesPlano plano;
  private final VoluntarioRepository voluntarios;
  private final IndisponibilidadeRepository itens;
  private final RespostaIndisponibilidadeRepository respostas;
  private final IndisponibilidadeMesService meses;
  private final AuditLogService audit;
  @PersistenceContext private EntityManager em;

  public DisponibilidadePortalService(
      PortalService portal,
      FuncionalidadesPlano plano,
      VoluntarioRepository voluntarios,
      IndisponibilidadeRepository itens,
      RespostaIndisponibilidadeRepository respostas,
      IndisponibilidadeMesService meses,
      AuditLogService audit) {
    this.portal = portal;
    this.plano = plano;
    this.voluntarios = voluntarios;
    this.itens = itens;
    this.respostas = respostas;
    this.meses = meses;
    this.audit = audit;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Resposta buscar(int ano, int mes) {
    var ym = validarMes(ano, mes);
    return resposta(pessoa(), ym);
  }

  @Transactional
  public Resposta salvar(int ano, int mes, Salvar req) {
    var ym = validarMes(ano, mes);
    var atual = YearMonth.now(ZoneId.of("America/Sao_Paulo"));
    if (ym.isBefore(atual) || ym.isAfter(atual.plusMonths(12)))
      throw new BadRequestException("Informe o mês atual ou um dos próximos 12 meses.");
    UUID pessoa = pessoa();
    if (req.semRestricao() && !req.itens().isEmpty())
      throw new BadRequestException("Sem restrição não pode conter datas bloqueadas.");
    var chaves = new HashSet<String>();
    var dias = new HashSet<LocalDate>();
    var inteiros = new HashSet<LocalDate>();
    for (var i : req.itens()) {
      if (!YearMonth.from(i.data()).equals(ym))
        throw new BadRequestException("A data não é do mês escolhido.");
      if (!chaves.add(i.data() + "|" + i.periodo()))
        throw new BadRequestException("Data e período repetidos.");
      if (i.periodo() == null) {
        if (dias.contains(i.data()))
          throw new BadRequestException("Dia inteiro não pode repetir períodos.");
        inteiros.add(i.data());
      } else if (inteiros.contains(i.data()))
        throw new BadRequestException("Dia inteiro não pode repetir períodos.");
      dias.add(i.data());
    }
    meses.avancar(ano, mes, req.versao());
    itens.deleteByVoluntarioIdAndDataBetween(pessoa, ym.atDay(1), ym.atEndOfMonth());
    respostas.deleteByVoluntarioIdAndAnoAndMes(pessoa, ano, mes);
    em.flush();
    itens.saveAll(
        req.itens().stream()
            .map(i -> new Indisponibilidade(pessoa, i.data(), i.periodo(), null))
            .toList());
    if (req.semRestricao()) respostas.save(new RespostaIndisponibilidade(pessoa, ano, mes, true));
    em.flush();
    audit.registrar(
        "RESPONDER",
        "INDISPONIBILIDADE_PORTAL",
        pessoa,
        List.of(ano + "/" + mes, "datas", "semRestricao"));
    return resposta(pessoa, ym);
  }

  private UUID pessoa() {
    plano.exigir("PORTAL_VOLUNTARIO");
    UUID id = portal.pessoaAtual();
    if (id == null || voluntarios.findById(id).filter(v -> v.isAtivo()).isEmpty())
      throw new BadRequestException("Sua conta precisa estar vinculada a um voluntário ativo.");
    return id;
  }

  private Resposta resposta(UUID id, YearMonth ym) {
    var lista =
        itens
            .findByVoluntarioIdAndDataBetweenOrderByDataAsc(id, ym.atDay(1), ym.atEndOfMonth())
            .stream()
            .map(i -> new Bloqueio(i.getData(), i.getPeriodo()))
            .toList();
    boolean sem =
        respostas.findByVoluntarioIdAndAnoAndMes(id, ym.getYear(), ym.getMonthValue()).stream()
            .anyMatch(RespostaIndisponibilidade::isSemRestricao);
    return new Resposta(
        ym.getYear(),
        ym.getMonthValue(),
        meses.versao(ym.getYear(), ym.getMonthValue()),
        sem,
        lista);
  }

  private YearMonth validarMes(int ano, int mes) {
    if (ano < 2000 || ano > 2100 || mes < 1 || mes > 12)
      throw new BadRequestException("Mês inválido.");
    return YearMonth.of(ano, mes);
  }
}
