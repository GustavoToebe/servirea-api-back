package br.com.servire.api.escala;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.escala.dto.CandidatoResponse;
import br.com.servire.api.escala.dto.EscalaEventoRequest;
import br.com.servire.api.escala.dto.EscalaRequest;
import br.com.servire.api.escala.dto.EscalaVagaRequest;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.Pessoas;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.DisponibilidadeVoluntario;
import br.com.servire.api.voluntario.DisponibilidadeVoluntarioRepository;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.Periodo;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.voluntario.VoluntarioRepository;
import br.com.servire.api.web.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Picker de candidatos (seção 49): filtros de ativo, função, duplicata no
 * evento e disponibilidade (recorrente/pontual + período).
 */
class CandidatoPickerIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate DATA_EVENTO = LocalDate.of(2026, 10, 4);
    private static final LocalTime HORARIO_NOITE = LocalTime.of(19, 0);

    @Autowired
    private EscalaService escalaService;

    @Autowired
    private VoluntarioRepository voluntarioRepository;

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private DisponibilidadeVoluntarioRepository disponibilidadeRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @BeforeEach
    void definirTenant() {
        String sufixo = UUID.randomUUID().toString();
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "TENANT-PICKER-" + sufixo, "tenant-picker-" + sufixo,
                "Paróquia de teste (picker)", Tenant.Status.ATIVO));
        TenantContext.set(tenant.getId());
    }

    @AfterEach
    void limparTenantContext() {
        TenantContext.clear();
    }

    @Test
    void eventoInexistenteLancaResourceNotFoundException() {
        assertThatThrownBy(() -> escalaService.listarCandidatos(UUID.randomUUID(), FuncaoEscala.MISSAL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ativoComFuncaoESemDisponibilidadeEntra() {
        Voluntario apto = voluntarioComFuncao("Ana Apta", FuncaoEscala.MISSAL);
        voluntarioComFuncao("Bruno Sem Funcao", FuncaoEscala.CRUZ);
        Voluntario inativo = voluntarioComFuncao("Carla Inativa", FuncaoEscala.MISSAL);
        inativo.setAtivo(false);
        voluntarioRepository.saveAndFlush(inativo);
        UUID eventoId = criarEventoVazio();

        List<CandidatoResponse> candidatos = escalaService.listarCandidatos(eventoId, FuncaoEscala.MISSAL);

        assertThat(candidatos).extracting(CandidatoResponse::nomeCompleto).containsExactly("Ana Apta");
        assertThat(candidatos.get(0).id()).isEqualTo(apto.getId());
    }

    @Test
    void jaAlocadoNoEventoNaoEntra() {
        Voluntario alocado = voluntarioComFuncao("Diego Alocado", FuncaoEscala.MISSAL);
        voluntarioComFuncao("Elena Livre", FuncaoEscala.MISSAL);
        UUID eventoId = criarEventoComVoluntario(alocado.getId());

        List<CandidatoResponse> candidatos = escalaService.listarCandidatos(eventoId, FuncaoEscala.MISSAL);

        assertThat(candidatos).extracting(CandidatoResponse::nomeCompleto).containsExactly("Elena Livre");
    }

    @Test
    void disponibilidadeIncompativelExcluiQuemJaCadastrou() {
        Voluntario soDeManha = voluntarioComFuncao("Fabio Manha", FuncaoEscala.MISSAL);
        disponibilidadeRepository.saveAndFlush(new DisponibilidadeVoluntario(
                soDeManha, DayOfWeek.SUNDAY, null, Periodo.MANHA, null));
        voluntarioComFuncao("Gina Sem Cadastro", FuncaoEscala.MISSAL);
        UUID eventoId = criarEventoVazio();

        List<CandidatoResponse> candidatos = escalaService.listarCandidatos(eventoId, FuncaoEscala.MISSAL);

        assertThat(candidatos).extracting(CandidatoResponse::nomeCompleto).containsExactly("Gina Sem Cadastro");
    }

    @Test
    void disponibilidadeRecorrenteNoPeriodoEntra() {
        Voluntario domingoNoite = voluntarioComFuncao("Hugo Domingo", FuncaoEscala.MISSAL);
        disponibilidadeRepository.saveAndFlush(new DisponibilidadeVoluntario(
                domingoNoite, DATA_EVENTO.getDayOfWeek(), null, Periodo.NOITE, null));
        UUID eventoId = criarEventoVazio();

        List<CandidatoResponse> candidatos = escalaService.listarCandidatos(eventoId, FuncaoEscala.MISSAL);

        assertThat(candidatos).extracting(CandidatoResponse::nomeCompleto).containsExactly("Hugo Domingo");
    }

    @Test
    void disponibilidadePontualNaDataEntra() {
        Voluntario pontual = voluntarioComFuncao("Iris Pontual", FuncaoEscala.MISSAL);
        disponibilidadeRepository.saveAndFlush(new DisponibilidadeVoluntario(
                pontual, null, DATA_EVENTO, Periodo.NOITE, null));
        UUID eventoId = criarEventoVazio();

        List<CandidatoResponse> candidatos = escalaService.listarCandidatos(eventoId, FuncaoEscala.MISSAL);

        assertThat(candidatos).extracting(CandidatoResponse::nomeCompleto).containsExactly("Iris Pontual");
    }

    @Test
    void periodoDeMapeiaManhaTardeNoite() {
        assertThat(EscalaService.periodoDe(LocalTime.of(8, 0))).isEqualTo(Periodo.MANHA);
        assertThat(EscalaService.periodoDe(LocalTime.of(12, 0))).isEqualTo(Periodo.TARDE);
        assertThat(EscalaService.periodoDe(LocalTime.of(19, 0))).isEqualTo(Periodo.NOITE);
    }

    private Voluntario voluntarioComFuncao(String nome, FuncaoEscala funcao) {
        Voluntario voluntario = Pessoas.persistirVoluntario(pessoaRepository, nome);
        voluntario.setFuncoesHabilitadas(new FuncaoEscala[]{funcao});
        return voluntarioRepository.saveAndFlush(voluntario);
    }

    private UUID criarEventoVazio() {
        Escala escala = escalaService.criar(new EscalaRequest(
                "Escala picker", TipoEscala.SEMANAL, 2026, 10, null, null,
                List.of(new EscalaEventoRequest(DATA_EVENTO, HORARIO_NOITE, "Missa",
                        List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, null))))), null);
        return escala.getEventos().get(0).getId();
    }

    private UUID criarEventoComVoluntario(UUID voluntarioId) {
        Escala escala = escalaService.criar(new EscalaRequest(
                "Escala picker", TipoEscala.SEMANAL, 2026, 10, null, null,
                List.of(new EscalaEventoRequest(DATA_EVENTO, HORARIO_NOITE, "Missa",
                        List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, voluntarioId))))), null);
        return escala.getEventos().get(0).getId();
    }
}
