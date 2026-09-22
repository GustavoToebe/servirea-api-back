package br.com.servire.api.escala;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.escala.dto.EscalaEventoRequest;
import br.com.servire.api.escala.dto.EscalaRequest;
import br.com.servire.api.escala.dto.EscalaVagaRequest;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.voluntario.VoluntarioRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de regra de negócio de {@link EscalaService} (débito técnico da
 * Fase 9, seção 46/47/109 do plano mestre, adiado até esta rodada —
 * 22/09/2026, junto com a Fase 10). Não precisa de {@code @MockitoBean}
 * — {@code EscalaService} não depende de nenhum serviço externo
 * (Storage/Turnstile), diferente de voluntário/inscrição.
 *
 * <p>Cada teste cria seu próprio tenant descartável, mesmo padrão das
 * outras classes de teste desta rodada — esta classe não usa rollback
 * automático por teste.</p>
 */
class EscalaServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EscalaService escalaService;

    @Autowired
    private EscalaRepository escalaRepository;

    @Autowired
    private VoluntarioRepository voluntarioRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @BeforeEach
    void definirTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "TENANT-ESCALA-SVC-TESTE", "tenant-escala-svc-teste-" + UUID.randomUUID(),
                "Paróquia de teste (escala service)", Tenant.Status.ATIVO));
        TenantContext.set(tenant.getId());
    }

    @AfterEach
    void limparTenantContext() {
        TenantContext.clear();
    }

    @Test
    void criarGravaEscalaComEventosEVagasEmCascata() {
        Escala criada = escalaService.criar(requestComUmEvento(null), null);

        assertThat(criada.getId()).isNotNull();
        assertThat(criada.getStatus()).isEqualTo(StatusEscala.RASCUNHO);
        assertThat(criada.getEventos()).hasSize(1);
        assertThat(criada.getEventos().get(0).getVagas()).hasSize(1);
    }

    /**
     * "Apaga tudo e reinsere" (mesmo padrão de {@code Voluntario}/
     * {@code Responsavel} na Fase 6) — atualizar com uma lista de eventos
     * diferente da original substitui completamente a coleção anterior
     * (orphanRemoval), não faz um merge incremental.
     */
    @Test
    void atualizarSubstituiTodosOsEventosAntigosPelosNovos() {
        Escala criada = escalaService.criar(requestComUmEvento(null), null);
        Long versaoOriginal = criada.getVersion();

        EscalaEventoRequest novoEvento = new EscalaEventoRequest(
                LocalDate.of(2026, 11, 1), LocalTime.of(10, 0), "Missa de Finados",
                List.of(new EscalaVagaRequest(FuncaoEscala.CRUZ, 1, null)));
        EscalaRequest atualizacao = new EscalaRequest(
                "Escala Atualizada", TipoEscala.SEMANAL, 2026, 11, null, versaoOriginal, List.of(novoEvento));

        Escala atualizada = escalaService.atualizar(criada.getId(), atualizacao);

        assertThat(atualizada.getEventos()).hasSize(1);
        assertThat(atualizada.getEventos().get(0).getCelebracao()).isEqualTo("Missa de Finados");
        assertThat(atualizada.getEventos().get(0).getVagas().get(0).getFuncao()).isEqualTo(FuncaoEscala.CRUZ);
    }

    @Test
    void atualizarComVersaoDivergenteLancaConflictException() {
        Escala criada = escalaService.criar(requestComUmEvento(null), null);
        EscalaRequest atualizacaoComVersaoErrada = new EscalaRequest(
                "Tentando Atualizar", TipoEscala.SEMANAL, 2026, 10,
                null, criada.getVersion() + 999, List.of(eventoSimples()));

        assertThatThrownBy(() -> escalaService.atualizar(criada.getId(), atualizacaoComVersaoErrada))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("alterada por outro usuário");
    }

    @Test
    void atualizarEscalaQueNaoEstaEmRascunhoLancaConflictException() {
        Escala criada = escalaService.criar(requestComUmEvento(null), null);
        escalaService.finalizar(criada.getId());
        EscalaRequest atualizacao = new EscalaRequest(
                "Tentando Atualizar", TipoEscala.SEMANAL, 2026, 10, null, criada.getVersion(), List.of(eventoSimples()));

        assertThatThrownBy(() -> escalaService.atualizar(criada.getId(), atualizacao))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void mesmoVoluntarioDuasVagasNoMesmoEventoLancaBadRequestException() {
        Voluntario voluntario = voluntarioRepository.saveAndFlush(new Voluntario("Voluntário Duplicado"));
        EscalaEventoRequest evento = new EscalaEventoRequest(
                LocalDate.of(2026, 10, 4), LocalTime.of(19, 0), "Missa",
                List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, voluntario.getId()),
                        new EscalaVagaRequest(FuncaoEscala.CRUZ, 2, voluntario.getId())));
        EscalaRequest request = new EscalaRequest("Escala com Conflito", TipoEscala.SEMANAL, 2026, 10, null, null, List.of(evento));

        assertThatThrownBy(() -> escalaService.criar(request, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("não pode ocupar duas vagas");
    }

    /**
     * Matriz de transição de estado (seção 46/109): finalizar só a partir
     * de RASCUNHO; cancelar a partir de RASCUNHO ou FINALIZADA; reabrir a
     * partir de FINALIZADA ou CANCELADA volta para RASCUNHO; excluir só
     * se CANCELADA.
     */
    @Test
    void transicoesDeEstadoSeguemAMatrizDaSecao46() {
        Escala rascunho = escalaService.criar(requestComUmEvento(null), null);
        assertThatThrownBy(() -> escalaService.reabrir(rascunho.getId()))
                .as("reabrir uma escala já em RASCUNHO não faz sentido")
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> escalaService.excluir(rascunho.getId()))
                .as("só se pode excluir uma escala CANCELADA")
                .isInstanceOf(ConflictException.class);

        Escala finalizada = escalaService.finalizar(rascunho.getId());
        assertThat(finalizada.getStatus()).isEqualTo(StatusEscala.FINALIZADA);
        assertThatThrownBy(() -> escalaService.finalizar(rascunho.getId()))
                .as("só se pode finalizar uma escala em RASCUNHO")
                .isInstanceOf(ConflictException.class);

        Escala reaberta = escalaService.reabrir(rascunho.getId());
        assertThat(reaberta.getStatus()).isEqualTo(StatusEscala.RASCUNHO);

        Escala cancelada = escalaService.cancelar(rascunho.getId());
        assertThat(cancelada.getStatus()).isEqualTo(StatusEscala.CANCELADA);
        assertThatThrownBy(() -> escalaService.cancelar(rascunho.getId()))
                .as("cancelar uma escala já cancelada não faz sentido")
                .isInstanceOf(ConflictException.class);

        Escala reabertaDeNovo = escalaService.reabrir(rascunho.getId());
        assertThat(reabertaDeNovo.getStatus()).isEqualTo(StatusEscala.RASCUNHO);

        Escala canceladaDeNovo = escalaService.cancelar(rascunho.getId());
        escalaService.excluir(canceladaDeNovo.getId());
        assertThat(escalaRepository.findById(canceladaDeNovo.getId())).isEmpty();
    }

    private EscalaRequest requestComUmEvento(Long version) {
        return new EscalaRequest("Escala de Teste", TipoEscala.SEMANAL, 2026, 10, null, version, List.of(eventoSimples()));
    }

    private EscalaEventoRequest eventoSimples() {
        return new EscalaEventoRequest(
                LocalDate.of(2026, 10, 4), LocalTime.of(19, 0), "Missa",
                List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, null)));
    }
}
