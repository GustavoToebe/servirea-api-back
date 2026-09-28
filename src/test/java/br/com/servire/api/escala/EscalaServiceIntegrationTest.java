package br.com.servire.api.escala;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.escala.dto.EscalaEventoRequest;
import br.com.servire.api.escala.dto.EscalaRequest;
import br.com.servire.api.escala.dto.EscalaVagaRequest;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.Pessoas;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.voluntario.VoluntarioRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
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
 * 22/09/2026, junto com a Fase 10; e da Fase 11 — {@code registrarPresenca},
 * seção 131.5 item 11, adicionado numa rodada posterior, 22/09/2026 à
 * tarde). Não precisa de {@code @MockitoBean}
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
    private EscalaVagaRepository escalaVagaRepository;

    @Autowired
    private VoluntarioRepository voluntarioRepository;

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private br.com.servire.api.voluntario.VoluntarioService voluntarioService;

    @BeforeEach
    void definirTenant() {
        // Mesmo bug de teste do VoluntarioServiceIntegrationTest (mvn clean
        // verify real de 22/09/2026): "codigo" (coluna UNIQUE) fixo enquanto
        // só o slug era randomizado.
        String sufixo = UUID.randomUUID().toString();
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "TENANT-ESCALA-SVC-TESTE-" + sufixo, "tenant-escala-svc-teste-" + sufixo,
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
        Voluntario voluntario = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário Duplicado");
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

    /**
     * Controle de faltas (Fase 11, seção 131.5 item 11) — débito de teste
     * pago nesta rodada (22/09/2026, junto com o restante da lacuna de
     * confirmação da Fase 11 — ver README/plano mestre). Nenhum destes
     * quatro testes existia quando o segundo {@code mvn clean verify} da
     * Fase 11 confirmou {@code BUILD SUCCESS}; até então
     * {@link EscalaService#registrarPresenca} nunca tinha sido chamado por
     * nenhum teste automatizado.
     */
    @Test
    void registrarPresencaAtualizaAPresencaDaVaga() {
        Voluntario voluntario = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário Presente");
        Escala criada = escalaService.criar(requestComUmEventoEVoluntario(voluntario.getId()), null);
        UUID vagaId = criada.getEventos().get(0).getVagas().get(0).getId();

        EscalaVaga atualizada = escalaService.registrarPresenca(vagaId, Presenca.PRESENTE);

        assertThat(atualizada.getPresenca()).isEqualTo(Presenca.PRESENTE);
        assertThat(escalaVagaRepository.findById(vagaId).orElseThrow().getPresenca()).isEqualTo(Presenca.PRESENTE);
    }

    @Test
    void registrarPresencaSemVoluntarioAlocadoLancaBadRequestException() {
        Escala criada = escalaService.criar(requestComUmEvento(null), null);
        UUID vagaId = criada.getEventos().get(0).getVagas().get(0).getId();

        assertThatThrownBy(() -> escalaService.registrarPresenca(vagaId, Presenca.FALTOU))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("não tem voluntário alocado");
    }

    @Test
    void registrarPresencaEmEscalaCanceladaLancaConflictException() {
        Voluntario voluntario = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário da Escala Cancelada");
        Escala criada = escalaService.criar(requestComUmEventoEVoluntario(voluntario.getId()), null);
        UUID vagaId = criada.getEventos().get(0).getVagas().get(0).getId();
        escalaService.cancelar(criada.getId());

        assertThatThrownBy(() -> escalaService.registrarPresenca(vagaId, Presenca.PRESENTE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CANCELADA");
    }

    @Test
    void registrarPresencaComVagaInexistenteLancaResourceNotFoundException() {
        assertThatThrownBy(() -> escalaService.registrarPresenca(UUID.randomUUID(), Presenca.PRESENTE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Uma referência (setembro) e um dia real (outubro), a mesma pessoa nos dois. */
    private Escala escalaComReferencia(UUID voluntarioId) {
        EscalaEventoRequest referencia = new EscalaEventoRequest(LocalDate.of(2026, 9, 29), LocalTime.of(19, 0), "Missa",
                List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, voluntarioId)), true);
        EscalaEventoRequest real = new EscalaEventoRequest(LocalDate.of(2026, 10, 1), LocalTime.of(19, 0), "Missa",
                List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, voluntarioId)), false);
        return escalaService.criar(new EscalaRequest("Semanal Outubro", TipoEscala.SEMANAL, 2026, 10, null, null,
                List.of(referencia, real)), null);
    }

    @Test
    void referenciaGravaVoltaENaoDeixaFinalizar() {
        Voluntario v = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário da Referência");
        Escala criada = escalaComReferencia(v.getId());
        Escala lida = escalaService.buscarPorId(criada.getId());
        assertThat(lida.getEventos()).extracting(EscalaEvento::isReferencia).containsExactlyInAnyOrder(true, false);

        assertThatThrownBy(() -> escalaService.finalizar(criada.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("linhas de referência");

        EscalaRequest semReferencia = new EscalaRequest("Semanal Outubro", TipoEscala.SEMANAL, 2026, 10, null,
                lida.getVersion(), List.of(new EscalaEventoRequest(LocalDate.of(2026, 10, 1), LocalTime.of(19, 0), "Missa",
                List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, v.getId())))));
        escalaService.atualizar(criada.getId(), semReferencia);
        assertThat(escalaService.finalizar(criada.getId()).getStatus()).isEqualTo(StatusEscala.FINALIZADA);
    }

    @Test
    void referenciaNaoRecebePresencaNemAlocacaoENaoEntraEmCompromissos() {
        Voluntario v = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário Só Referência");
        Escala criada = escalaComReferencia(v.getId());
        EscalaEvento ref = criada.getEventos().stream().filter(EscalaEvento::isReferencia).findFirst().orElseThrow();
        UUID vagaRef = ref.getVagas().get(0).getId();

        assertThatThrownBy(() -> escalaService.registrarPresenca(vagaRef, Presenca.PRESENTE))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("referência");
        assertThatThrownBy(() -> escalaService.alocarVaga(vagaRef, null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("referência");
        assertThat(escalaService.listarCandidatos(ref.getId(), null)).isEmpty();

        assertThat(voluntarioService.listarCompromissos(v.getId()))
                .extracting(c -> c.data()).containsExactly(LocalDate.of(2026, 10, 1));
    }

    private EscalaRequest requestComUmEvento(Long version) {
        return new EscalaRequest("Escala de Teste", TipoEscala.SEMANAL, 2026, 10, null, version, List.of(eventoSimples()));
    }

    private EscalaRequest requestComUmEventoEVoluntario(UUID voluntarioId) {
        EscalaEventoRequest evento = new EscalaEventoRequest(
                LocalDate.of(2026, 10, 4), LocalTime.of(19, 0), "Missa",
                List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, voluntarioId)));
        return new EscalaRequest("Escala de Teste", TipoEscala.SEMANAL, 2026, 10, null, null, List.of(evento));
    }

    private EscalaEventoRequest eventoSimples() {
        return new EscalaEventoRequest(
                LocalDate.of(2026, 10, 4), LocalTime.of(19, 0), "Missa",
                List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, null)));
    }
}
