package br.com.servire.api.voluntario;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.escala.Escala;
import br.com.servire.api.escala.EscalaService;
import br.com.servire.api.escala.StatusEscala;
import br.com.servire.api.escala.TipoEscala;
import br.com.servire.api.escala.dto.EscalaEventoRequest;
import br.com.servire.api.escala.dto.EscalaRequest;
import br.com.servire.api.escala.dto.EscalaVagaRequest;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.dto.CompromissoResponse;
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
 * {@code GET /voluntarios/{id}/commitments} — equivalente à view
 * {@code vw_voluntario_compromissos} (V010), via JPA.
 */
class CompromissoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private VoluntarioService voluntarioService;

    @Autowired
    private VoluntarioRepository voluntarioRepository;

    @Autowired
    private EscalaService escalaService;

    @Autowired
    private TenantRepository tenantRepository;

    @BeforeEach
    void definirTenant() {
        String sufixo = UUID.randomUUID().toString();
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "TENANT-COMPR-" + sufixo, "tenant-compr-" + sufixo,
                "Paróquia de teste (compromissos)", Tenant.Status.ATIVO));
        TenantContext.set(tenant.getId());
    }

    @AfterEach
    void limparTenantContext() {
        TenantContext.clear();
    }

    @Test
    void voluntarioInexistenteLancaResourceNotFoundException() {
        assertThatThrownBy(() -> voluntarioService.listarCompromissos(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void semVagaAlocadaDevolveListaVazia() {
        Voluntario voluntario = voluntarioRepository.saveAndFlush(new Voluntario("Sem Compromisso"));

        assertThat(voluntarioService.listarCompromissos(voluntario.getId())).isEmpty();
    }

    @Test
    void vagaAlocadaApareceComoCompromisso() {
        Voluntario voluntario = voluntarioRepository.saveAndFlush(new Voluntario("Escalado"));
        LocalDate data = LocalDate.of(2026, 10, 4);
        LocalTime horario = LocalTime.of(19, 0);
        Escala escala = escalaService.criar(new EscalaRequest(
                "Escala de Outubro", TipoEscala.SEMANAL, 2026, 10, null, null,
                List.of(new EscalaEventoRequest(data, horario, "Missa",
                        List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, voluntario.getId()))))), null);

        List<CompromissoResponse> compromissos = voluntarioService.listarCompromissos(voluntario.getId());

        assertThat(compromissos).hasSize(1);
        CompromissoResponse c = compromissos.get(0);
        assertThat(c.voluntarioId()).isEqualTo(voluntario.getId());
        assertThat(c.escalaId()).isEqualTo(escala.getId());
        assertThat(c.escalaTitulo()).isEqualTo("Escala de Outubro");
        assertThat(c.escalaStatus()).isEqualTo(StatusEscala.RASCUNHO);
        assertThat(c.data()).isEqualTo(data);
        assertThat(c.horario()).isEqualTo(horario);
        assertThat(c.celebracao()).isEqualTo("Missa");
        assertThat(c.funcao()).isEqualTo(FuncaoEscala.MISSAL);
    }

    @Test
    void outroVoluntarioNaoVeOCompromisso() {
        Voluntario escalado = voluntarioRepository.saveAndFlush(new Voluntario("Escalado"));
        Voluntario outro = voluntarioRepository.saveAndFlush(new Voluntario("Outro"));
        escalaService.criar(new EscalaRequest(
                "Escala", TipoEscala.SEMANAL, 2026, 10, null, null,
                List.of(new EscalaEventoRequest(LocalDate.of(2026, 10, 4), LocalTime.of(19, 0), "Missa",
                        List.of(new EscalaVagaRequest(FuncaoEscala.CRUZ, 1, escalado.getId()))))), null);

        assertThat(voluntarioService.listarCompromissos(outro.getId())).isEmpty();
        assertThat(voluntarioService.listarCompromissos(escalado.getId())).hasSize(1);
    }
}
