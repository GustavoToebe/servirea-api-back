package br.com.servire.api.acesso;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.security.JwtService;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.function.Function;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /me} devolve os códigos efetivos do catálogo. O menu do front
 * esconde o que não está nessa lista; quem não tem a permissão continua
 * recebendo 403 no endpoint.
 */
@AutoConfigureMockMvc
class MeHttpIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioTenantRepository usuarioTenantRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PerfilRepository perfilRepository;

    @AfterEach
    void limparTenantContext() {
        TenantContext.clear();
    }

    @Test
    void semTokenRecebe401() throws Exception {
        mockMvc.perform(get("/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/perfis").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/tenant").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administradorRecebeTodasAsPermissoesEEntraNosCadastros() throws Exception {
        String token = tokenDe(UsuarioTenant.Role.ADMIN, null);

        mockMvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissoes", hasItem("PERFIL")))
                .andExpect(jsonPath("$.permissoes", hasItem("USUARIO")))
                .andExpect(jsonPath("$.permissoes", hasItem("PAROQUIA")))
                .andExpect(jsonPath("$.permissoes", hasItem("AUDITORIA")))
                .andExpect(jsonPath("$.permissoes", hasItem("PESSOA")))
                .andExpect(jsonPath("$.permissoes", hasItem("ESCALA")));

        mockMvc.perform(get("/perfis").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/tenant").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void secretarioVeParoquiaEPessoasMasNaoPerfis() throws Exception {
        String token = tokenDe(UsuarioTenant.Role.VISUALIZADOR, PerfisPadrao::secretario);

        mockMvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value(PerfisPadrao.SECRETARIO))
                .andExpect(jsonPath("$.permissoes", hasItem("PAROQUIA")))
                .andExpect(jsonPath("$.permissoes", hasItem("PESSOA")))
                .andExpect(jsonPath("$.permissoes", hasItem("ESCALA")))
                .andExpect(jsonPath("$.permissoes", not(hasItem("PERFIL"))))
                .andExpect(jsonPath("$.permissoes", not(hasItem("USUARIO"))))
                .andExpect(jsonPath("$.permissoes", not(hasItem("AUDITORIA"))));

        mockMvc.perform(get("/tenant").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/perfis").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordenadorVeEscalasERecebe403EmPerfilEParoquia() throws Exception {
        String token = tokenDe(UsuarioTenant.Role.VISUALIZADOR, PerfisPadrao::coordenador);

        mockMvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value(PerfisPadrao.COORDENADOR))
                .andExpect(jsonPath("$.permissoes", hasItem("ESCALA")))
                .andExpect(jsonPath("$.permissoes", hasItem("VAGA")))
                .andExpect(jsonPath("$.permissoes", hasItem("PESSOA")))
                .andExpect(jsonPath("$.permissoes", not(hasItem("PERFIL"))))
                .andExpect(jsonPath("$.permissoes", not(hasItem("USUARIO"))))
                .andExpect(jsonPath("$.permissoes", not(hasItem("PAROQUIA"))))
                .andExpect(jsonPath("$.permissoes", not(hasItem("AUDITORIA"))));

        mockMvc.perform(get("/perfis").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/tenant").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String tokenDe(UsuarioTenant.Role role, Function<UUID, Perfil> perfil) {
        String sufixo = UUID.randomUUID().toString();
        Usuario usuario = usuarioRepository.saveAndFlush(new Usuario("me-" + sufixo + "@teste.com", "Usuário " + sufixo));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "ME-" + sufixo.substring(0, 8), "me-" + sufixo, "Paróquia me HTTP", Tenant.Status.ATIVO));
        UsuarioTenant vinculo = new UsuarioTenant(usuario, tenant, role, UsuarioTenant.Status.ATIVO);
        if (perfil != null) {
            vinculo.setPerfil(perfilRepository.saveAndFlush(perfil.apply(tenant.getId())));
        }
        usuarioTenantRepository.saveAndFlush(vinculo);
        return jwtService.gerarAccessToken(usuario.getId(), tenant.getId(), role);
    }
}
