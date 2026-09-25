package br.com.servire.api.security;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.acesso.Perfil;
import br.com.servire.api.acesso.PerfilRepository;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.integracao.DireitosLocais;
import br.com.servire.api.integracao.DireitosLocaisRepository;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    @Autowired
    private DireitosLocaisRepository direitosLocaisRepository;

    @Autowired
    private PerfilRepository perfilRepository;

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
    void accessTokenComClaimDeSuporteDoPainelAntigoNaoAutentica() throws Exception {
        Tenant tenant = tenant("jwt-sup-antigo", Tenant.Status.ATIVO);
        Usuario usuario = usuarioComVinculo("jwt-sup-antigo", tenant);
        String token = jwtService.gerarAccessToken(usuario.getId(), tenant.getId(), UsuarioTenant.Role.ADMIN, true);

        mockMvc.perform(get("/voluntarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeSuporteDaCentralEntraEmParoquiaBloqueada() throws Exception {
        Tenant tenant = tenant("jwt-sup-app", Tenant.Status.BLOQUEADO);
        String token = jwtService.gerarTokenSuporte(
                UUID.randomUUID(), tenant.getId(), "Operador", "op@central.com", "Teste de suporte");

        mockMvc.perform(get("/voluntarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void paroquiaSemConfirmacaoDaCentralHaMaisDaToleranciaNaoAutentica() throws Exception {
        Tenant tenant = tenant("jwt-72h", Tenant.Status.ATIVO);
        Usuario usuario = usuarioComVinculo("jwt-72h", tenant);
        DireitosLocais direitos = new DireitosLocais(tenant.getId(), UUID.randomUUID());
        direitos.setVersao(1);
        direitos.setSituacao("ATIVA");
        direitos.setAcessoLiberado(true);
        direitos.setConfirmadoEm(Instant.now().minus(73, ChronoUnit.HOURS));
        direitosLocaisRepository.saveAndFlush(direitos);
        String token = jwtService.gerarAccessToken(usuario.getId(), tenant.getId(), UsuarioTenant.Role.ADMIN);

        mockMvc.perform(get("/voluntarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        direitos.setConfirmadoEm(Instant.now().minus(71, ChronoUnit.HOURS));
        direitosLocaisRepository.saveAndFlush(direitos);
        mockMvc.perform(get("/voluntarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioEmPerfilInativoNaoAutentica() throws Exception {
        Tenant tenant = tenant("jwt-perfil-inativo", Tenant.Status.ATIVO);
        Perfil perfil = new Perfil(tenant.getId(), "Inativo", false, false);
        perfil.substituirPermissoes(List.of("PESSOA"));
        perfil.setAtivo(false);
        perfil = perfilRepository.saveAndFlush(perfil);
        Usuario usuario = usuarioRepository.saveAndFlush(
                new Usuario("jwt-perfil-inativo-" + UUID.randomUUID() + "@teste.com", "Usuário"));
        UsuarioTenant vinculo = new UsuarioTenant(usuario, tenant, UsuarioTenant.Role.VISUALIZADOR, UsuarioTenant.Status.ATIVO);
        vinculo.setPerfil(perfil);
        usuarioTenantRepository.saveAndFlush(vinculo);
        String token = jwtService.gerarAccessToken(usuario.getId(), tenant.getId(), UsuarioTenant.Role.VISUALIZADOR);

        mockMvc.perform(get("/voluntarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private Tenant tenant(String rotulo, Tenant.Status status) {
        return tenantRepository.saveAndFlush(new Tenant(
                "J-" + UUID.randomUUID().toString().substring(0, 8),
                rotulo + "-" + UUID.randomUUID(),
                "Paróquia " + rotulo,
                status));
    }

    private Usuario usuarioComVinculo(String rotulo, Tenant tenant) {
        Usuario usuario = usuarioRepository.saveAndFlush(
                new Usuario(rotulo + "-" + UUID.randomUUID() + "@teste.com", "Usuário " + rotulo));
        usuarioTenantRepository.saveAndFlush(
                new UsuarioTenant(usuario, tenant, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO));
        return usuario;
    }
}
