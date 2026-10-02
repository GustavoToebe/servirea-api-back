package br.com.servire.api.escala;

import br.com.servire.api.escala.MotorDistribuicao.*;
import br.com.servire.api.voluntario.FuncaoEscala;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class MotorDistribuicaoTest {
    static final LocalDate SABADO = LocalDate.of(2026, 11, 7);
    static final Regras REGRAS = new Regras(2, 0, false);

    Pessoa pessoa(String nome, FuncaoEscala... f) {
        return new Pessoa(UUID.randomUUID(), nome, f.length == 0 ? Set.of() : EnumSet.copyOf(List.of(f)), List.of(), List.of(), Set.of());
    }

    Vaga vaga(UUID evento, LocalDate data, int hora, FuncaoEscala f, UUID atual) {
        return new Vaga(UUID.randomUUID(), evento, data, LocalTime.of(hora, 0), f, atual, 0);
    }

    @Test
    void equilibraCargaEEsDeterministico() {
        var a = pessoa("A", FuncaoEscala.MISSAL);
        var b = pessoa("B", FuncaoEscala.MISSAL);
        var v1 = vaga(UUID.randomUUID(), SABADO, 19, FuncaoEscala.MISSAL, null);
        var v2 = vaga(UUID.randomUUID(), SABADO.plusDays(7), 19, FuncaoEscala.MISSAL, null);
        var r1 = MotorDistribuicao.gerar(List.of(v1, v2), List.of(a, b), List.of(), REGRAS);
        var r2 = MotorDistribuicao.gerar(List.of(v1, v2), List.of(b, a), List.of(), REGRAS);
        assertThat(r1.sugestoes()).hasSize(2);
        assertThat(r1.sugestoes().stream().map(Sugestao::pessoaId).distinct()).hasSize(2);
        assertThat(r1.sugestoes()).isEqualTo(r2.sugestoes());
        assertThat(r1.conflitos()).isEmpty();
    }

    @Test
    void explicaVagaSemCandidato() {
        var so = pessoa("Só cruz", FuncaoEscala.CRUZ);
        var v = vaga(UUID.randomUUID(), SABADO, 19, FuncaoEscala.MISSAL, null);
        var r = MotorDistribuicao.gerar(List.of(v), List.of(so), List.of(), REGRAS);
        assertThat(r.sugestoes()).isEmpty();
        assertThat(r.conflitos()).singleElement().satisfies(c -> assertThat(c.descartes()).containsEntry("FUNCAO_NAO_HABILITADA", 1));
    }

    @Test
    void respeitaIndisponibilidadeDiaEPeriodo() {
        var p = new Pessoa(UUID.randomUUID(), "P", EnumSet.of(FuncaoEscala.MISSAL), List.of(),
                List.of(new Negativa(SABADO, null)), Set.of());
        var v = vaga(UUID.randomUUID(), SABADO, 19, FuncaoEscala.MISSAL, null);
        var r = MotorDistribuicao.gerar(List.of(v), List.of(p), List.of(), REGRAS);
        assertThat(r.conflitos().getFirst().descartes()).containsEntry("INDISPONIVEL", 1);
    }

    @Test
    void naoColocaMesmaPessoaDuasVezesNoMesmoEvento() {
        var a = pessoa("A", FuncaoEscala.MISSAL, FuncaoEscala.CRUZ);
        var evento = UUID.randomUUID();
        var v1 = vaga(evento, SABADO, 19, FuncaoEscala.MISSAL, null);
        var v2 = vaga(evento, SABADO, 19, FuncaoEscala.CRUZ, null);
        var r = MotorDistribuicao.gerar(List.of(v1, v2), List.of(a), List.of(), REGRAS);
        assertThat(r.sugestoes()).hasSize(1);
        assertThat(r.conflitos()).hasSize(1);
        assertThat(r.conflitos().getFirst().descartes()).containsEntry("HORARIO_OCUPADO", 1);
    }

    @Test
    void respeitaLimiteEIntervaloComOutrasEscalas() {
        var a = pessoa("A", FuncaoEscala.MISSAL);
        var v = vaga(UUID.randomUUID(), SABADO, 19, FuncaoEscala.MISSAL, null);
        var outra = new Reserva(a.id(), UUID.randomUUID(), SABADO.minusDays(2), LocalTime.of(19, 0));
        var comIntervalo = MotorDistribuicao.gerar(List.of(v), List.of(a), List.of(outra), new Regras(5, 7, false));
        assertThat(comIntervalo.conflitos().getFirst().descartes()).containsEntry("INTERVALO_INSUFICIENTE", 1);
        var limite = MotorDistribuicao.gerar(List.of(v), List.of(a), List.of(outra, outra), new Regras(2, 0, false));
        assertThat(limite.conflitos().getFirst().descartes()).containsEntry("LIMITE_POR_PESSOA", 1);
    }

    @Test
    void exigeRespostaDoMesQuandoSolicitado() {
        var a = pessoa("A", FuncaoEscala.MISSAL);
        var v = vaga(UUID.randomUUID(), SABADO, 19, FuncaoEscala.MISSAL, null);
        var r = MotorDistribuicao.gerar(List.of(v), List.of(a), List.of(), new Regras(2, 0, true));
        assertThat(r.conflitos().getFirst().descartes()).containsEntry("RESPOSTA_PENDENTE", 1);
        var respondeu = new Pessoa(a.id(), "A", a.funcoes(), List.of(), List.of(), Set.of(YearMonth.from(SABADO)));
        assertThat(MotorDistribuicao.gerar(List.of(v), List.of(respondeu), List.of(), new Regras(2, 0, true)).sugestoes()).hasSize(1);
    }

    @Test
    void alocacaoExistenteInvalidaBloqueia() {
        var a = pessoa("A", FuncaoEscala.CRUZ);
        var v = vaga(UUID.randomUUID(), SABADO, 19, FuncaoEscala.MISSAL, a.id());
        var r = MotorDistribuicao.gerar(List.of(v), List.of(a), List.of(), REGRAS);
        assertThat(r.bloqueado()).isTrue();
        assertThat(r.conflitos().getFirst().alocacaoExistente()).isTrue();
        assertThat(r.sugestoes()).isEmpty();
    }
}
