package br.com.servire.api.security;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita o {@link JwtAuthenticationFilter} de verdade — access token
 * real no header {@code Authorization}, sem
 * {@code SecurityMockMvcRequestPostProcessors.authentication} (aquele
 * caminho do {@code MethodSecurityIntegrationTest} nunca chama
 * {@code vinculo.getTenant()} e por isso não pegaria o Bug real #15).
 *
 * <p>O sintoma em HTTP real era 401 com {@code path=/error} e
 * {@code requestId=null}: {@code getTenant().getStatus()} fora de
 * transação, com {@code open-in-view: false}, estourava
 * {@code LazyInitializationException}; o Boot despachava para
 * {@code /error}; o filtro (OncePerRequestFilter) não roda no ERROR
 * dispatch; {@code /error} exigia autenticação e mascarava o 500.</p>
 */
@AutoConfigureMockMvc
class JwtAuthenticationFilterIntegrationTest extends AbstractIntegrationTest {

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

    @Test
    void accessTokenValidoAutenticaGetVoluntarios() throws Exception {
        Usuario usuario = usuarioRepository.saveAndFlush(
                new Usuario("jwt-filter-" + UUID.randomUUID() + "@teste.com", "Usuario JWT Filter"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "JWT-FILTER-" + UUID.randomUUID().toString().substring(0, 8),
                "jwt-filter-" + UUID.randomUUID(),
                "Paróquia JWT Filter",
                Tenant.Status.ATIVO));
        usuarioTenantRepository.saveAndFlush(
                new UsuarioTenant(usuario, tenant, UsuarioTenant.Role.COORDENADOR, UsuarioTenant.Status.ATIVO));

        String token = jwtService.gerarAccessToken(usuario.getId(), tenant.getId(), UsuarioTenant.Role.COORDENADOR);

        mockMvc.perform(get("/voluntarios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void accessTokenDeTenantBloqueadoNaoAutentica() throws Exception {
        Usuario usuario = usuarioRepository.saveAndFlush(
                new Usuario("jwt-filter-bloqueado-" + UUID.randomUUID() + "@teste.com", "Usuario Bloqueado"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "JWT-BLOQ-" + UUID.randomUUID().toString().substring(0, 8),
                "jwt-bloqueado-" + UUID.randomUUID(),
                "Paróquia bloqueada",
                Tenant.Status.BLOQUEADO));
        usuarioTenantRepository.saveAndFlush(
                new UsuarioTenant(usuario, tenant, UsuarioTenant.Role.COORDENADOR, UsuarioTenant.Status.ATIVO));

        String token = jwtService.gerarAccessToken(usuario.getId(), tenant.getId(), UsuarioTenant.Role.COORDENADOR);

        mockMvc.perform(get("/voluntarios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/voluntarios"));
    }

    @Test
    void tokenBackofficeAutenticaAdminParoquiasENaoVoluntarios() throws Exception {
        Usuario operador = usuarioRepository.saveAndFlush(operador("jwt-backoffice"));
        String token = jwtService.gerarBackofficeToken(operador.getId());

        mockMvc.perform(get("/admin/paroquias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/voluntarios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenDeSuporteEntraEmParoquiaBloqueada() throws Exception {
        Usuario operador = usuarioRepository.saveAndFlush(operador("jwt-suporte"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "JWT-SUP-" + UUID.randomUUID().toString().substring(0, 8),
                "jwt-suporte-" + UUID.randomUUID(),
                "Paróquia suporte",
                Tenant.Status.BLOQUEADO));
        String token = jwtService.gerarAccessToken(
                operador.getId(), tenant.getId(), UsuarioTenant.Role.ADMIN, true);

        mockMvc.perform(get("/voluntarios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void padreNaoAutenticaRotasDoBackoffice() throws Exception {
        Usuario usuario = usuarioRepository.saveAndFlush(
                new Usuario("jwt-padre-" + UUID.randomUUID() + "@teste.com", "Padre JWT"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "JWT-PADRE-" + UUID.randomUUID().toString().substring(0, 8),
                "jwt-padre-" + UUID.randomUUID(),
                "Paróquia padre",
                Tenant.Status.ATIVO));
        usuarioTenantRepository.saveAndFlush(
                new UsuarioTenant(usuario, tenant, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO));
        String token = jwtService.gerarAccessToken(usuario.getId(), tenant.getId(), UsuarioTenant.Role.ADMIN);

        mockMvc.perform(get("/admin/paroquias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    private Usuario operador(String rotulo) {
        Usuario usuario = new Usuario(rotulo + "-" + UUID.randomUUID() + "@teste.com", "Operador " + rotulo);
        usuario.setOperadorSaas(true);
        return usuario;
    }
}
