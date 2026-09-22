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
 * {@code PATCH /escalas/vagas/{vagaId}} — aloca/desaloca sem reenviar a
 * escala inteira (complemento do picker, seção 49).
 */
class AlocacaoVagaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EscalaService escalaService;

    @Autowired
    private EscalaVagaRepository escalaVagaRepository;

    @Autowired
    private VoluntarioRepository voluntarioRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @BeforeEach
    void definirTenant() {
        String sufixo = UUID.randomUUID().toString();
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "TENANT-ALOC-" + sufixo, "tenant-aloc-" + sufixo,
                "Paróquia de teste (alocação)", Tenant.Status.ATIVO));
        TenantContext.set(tenant.getId());
    }

    @AfterEach
    void limparTenantContext() {
        TenantContext.clear();
    }

    @Test
    void vagaInexistenteLancaResourceNotFoundException() {
        assertThatThrownBy(() -> escalaService.alocarVaga(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vaga");
    }

    @Test
    void alocaVoluntarioAtivoNaVagaVazia() {
        Voluntario voluntario = voluntario("Ana Apta");
        UUID vagaId = criarEscalaComVagaVazia().getEventos().get(0).getVagas().get(0).getId();

        EscalaVaga alocada = escalaService.alocarVaga(vagaId, voluntario.getId());

        assertThat(alocada.getVoluntario().getId()).isEqualTo(voluntario.getId());
        assertThat(alocada.getPresenca()).isEqualTo(Presenca.PENDENTE);
        assertThat(escalaVagaRepository.findById(vagaId).orElseThrow().getVoluntario().getId())
                .isEqualTo(voluntario.getId());
    }

    @Test
    void voluntarioInexistenteLancaResourceNotFoundException() {
        UUID vagaId = criarEscalaComVagaVazia().getEventos().get(0).getVagas().get(0).getId();

        assertThatThrownBy(() -> escalaService.alocarVaga(vagaId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Voluntário");
    }

    @Test
    void voluntarioInativoLancaBadRequestException() {
        Voluntario inativo = voluntario("Carla Inativa");
        inativo.setAtivo(false);
        voluntarioRepository.saveAndFlush(inativo);
        UUID vagaId = criarEscalaComVagaVazia().getEventos().get(0).getVagas().get(0).getId();

        assertThatThrownBy(() -> escalaService.alocarVaga(vagaId, inativo.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    void jaAlocadoNoMesmoEventoLancaBadRequestException() {
        Voluntario alocado = voluntario("Diego Alocado");
        Escala escala = escalaService.criar(new EscalaRequest(
                "Escala alocação", TipoEscala.SEMANAL, 2026, 10, null, null,
                List.of(new EscalaEventoRequest(LocalDate.of(2026, 10, 4), LocalTime.of(19, 0), "Missa",
                        List.of(
                                new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, alocado.getId()),
                                new EscalaVagaRequest(FuncaoEscala.CRUZ, 1, null))))), null);
        UUID vagaLivre = escala.getEventos().get(0).getVagas().stream()
                .filter(v -> v.getVoluntario() == null)
                .findFirst().orElseThrow().getId();

        assertThatThrownBy(() -> escalaService.alocarVaga(vagaLivre, alocado.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("duas vagas");
    }

    @Test
    void escalaCanceladaLancaConflictException() {
        Voluntario voluntario = voluntario("Elena Cancelada");
        Escala escala = criarEscalaComVagaVazia();
        UUID vagaId = escala.getEventos().get(0).getVagas().get(0).getId();
        escalaService.cancelar(escala.getId());

        assertThatThrownBy(() -> escalaService.alocarVaga(vagaId, voluntario.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("RASCUNHO");
    }

    @Test
    void escalaFinalizadaLancaConflictException() {
        Voluntario voluntario = voluntario("Fabio Finalizada");
        Escala escala = criarEscalaComVagaVazia();
        UUID vagaId = escala.getEventos().get(0).getVagas().get(0).getId();
        escalaService.finalizar(escala.getId());

        assertThatThrownBy(() -> escalaService.alocarVaga(vagaId, voluntario.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("RASCUNHO");
    }

    @Test
    void desalocaEZeraPresenca() {
        Voluntario voluntario = voluntario("Gina Desalocada");
        Escala escala = escalaService.criar(new EscalaRequest(
                "Escala alocação", TipoEscala.SEMANAL, 2026, 10, null, null,
                List.of(new EscalaEventoRequest(LocalDate.of(2026, 10, 4), LocalTime.of(19, 0), "Missa",
                        List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, voluntario.getId()))))), null);
        UUID vagaId = escala.getEventos().get(0).getVagas().get(0).getId();
        escalaService.registrarPresenca(vagaId, Presenca.PRESENTE);

        EscalaVaga vazia = escalaService.alocarVaga(vagaId, null);

        assertThat(vazia.getVoluntario()).isNull();
        assertThat(vazia.getPresenca()).isEqualTo(Presenca.PENDENTE);
        EscalaVaga persistida = escalaVagaRepository.findById(vagaId).orElseThrow();
        assertThat(persistida.getVoluntario()).isNull();
        assertThat(persistida.getPresenca()).isEqualTo(Presenca.PENDENTE);
    }

    @Test
    void trocaVoluntarioZeraPresenca() {
        Voluntario primeiro = voluntario("Hugo Primeiro");
        Voluntario segundo = voluntario("Iris Segunda");
        Escala escala = escalaService.criar(new EscalaRequest(
                "Escala alocação", TipoEscala.SEMANAL, 2026, 10, null, null,
                List.of(new EscalaEventoRequest(LocalDate.of(2026, 10, 4), LocalTime.of(19, 0), "Missa",
                        List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, primeiro.getId()))))), null);
        UUID vagaId = escala.getEventos().get(0).getVagas().get(0).getId();
        escalaService.registrarPresenca(vagaId, Presenca.FALTOU);

        EscalaVaga trocada = escalaService.alocarVaga(vagaId, segundo.getId());

        assertThat(trocada.getVoluntario().getId()).isEqualTo(segundo.getId());
        assertThat(trocada.getPresenca()).isEqualTo(Presenca.PENDENTE);
    }

    private Voluntario voluntario(String nome) {
        return voluntarioRepository.saveAndFlush(new Voluntario(nome));
    }

    private Escala criarEscalaComVagaVazia() {
        return escalaService.criar(new EscalaRequest(
                "Escala alocação", TipoEscala.SEMANAL, 2026, 10, null, null,
                List.of(new EscalaEventoRequest(LocalDate.of(2026, 10, 4), LocalTime.of(19, 0), "Missa",
                        List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, null))))), null);
    }
}
