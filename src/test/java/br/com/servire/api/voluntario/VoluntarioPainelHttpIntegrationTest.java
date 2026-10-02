package br.com.servire.api.voluntario;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.Pessoas;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class VoluntarioPainelHttpIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired PessoaRepository pessoas;

    UUID tenant;

    @BeforeEach
    void preparar() {
        String slug = UUID.randomUUID().toString();
        tenant = tenants.saveAndFlush(new Tenant(slug, slug, "Painel", Tenant.Status.ATIVO)).getId();
        TenantContext.set(tenant);
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        voluntario("Coroinha", TipoVoluntario.COROINHA, true, null);
        voluntario("Acólito", TipoVoluntario.ACOLITO, true, null);
        voluntario("Ambos", TipoVoluntario.AMBOS, true, null);
        voluntario("Ministro a vencer", TipoVoluntario.MESC, true, hoje.plusDays(30));
        voluntario("Ministro vencido", TipoVoluntario.MESC, true, hoje.minusDays(10));
        voluntario("Ministro longe", TipoVoluntario.MESC, true, hoje.plusDays(400));
        voluntario("Inativo", TipoVoluntario.COROINHA, false, hoje.plusDays(5));
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    void voluntario(String nome, TipoVoluntario tipo, boolean ativo, LocalDate mandatoFim) {
        var p = Pessoas.voluntario(nome);
        p.getVoluntario().setTipo(tipo);
        p.getVoluntario().setAtivo(ativo);
        p.getVoluntario().setMandatoFim(mandatoFim);
        pessoas.saveAndFlush(p);
    }

    UsernamePasswordAuthenticationToken auth(String... permissoes) {
        TenantContext.set(tenant);
        return UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(UUID.randomUUID(), tenant, UsuarioTenant.Role.ADMIN, false),
                null, Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void contaNoBancoSemCarregarFichasEIncluiAmbosNasDuasFuncoes() throws Exception {
        mvc.perform(get("/voluntarios/painel").with(authentication(auth("PERM_ESCALA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativos").value(6))
                .andExpect(jsonPath("$.coroinhas").value(2))
                .andExpect(jsonPath("$.acolitos").value(2))
                .andExpect(jsonPath("$.mesc").value(3))
                .andExpect(jsonPath("$.mandatosAVencer").value(2));
    }

    @Test
    void semPermissaoDeLeituraNaoConsulta() throws Exception {
        mvc.perform(get("/voluntarios/painel").with(authentication(auth("PERM_FINANCEIRO")))).andExpect(status().isForbidden());
    }
}
