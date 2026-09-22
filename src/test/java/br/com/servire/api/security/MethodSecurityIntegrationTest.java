package br.com.servire.api.security;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.escala.EscalaService;
import br.com.servire.api.escala.EscalaVaga;
import br.com.servire.api.inscricao.Inscricao;
import br.com.servire.api.inscricao.InscricaoService;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantService;
import br.com.servire.api.voluntario.DisponibilidadeVoluntarioService;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.voluntario.VoluntarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fecha a maior lacuna de confirmação deixada pela Fase 11 (seção 31 do
 * plano mestre): até esta rodada, {@code @PreAuthorize}/
 * {@code @EnableMethodSecurity} nunca tinham sido exercitados por um teste
 * HTTP de verdade — só os serviços eram chamados diretamente
 * ({@code VoluntarioServiceIntegrationTest} etc.), o que nunca passa pela
 * checagem de autorização (aplicada só nos métodos dos controllers).
 *
 * <p><b>Estratégia:</b> os serviços por trás de cada controller são
 * substituídos por {@code @MockitoBean} — este teste não quer (nem
 * precisa) exercitar regra de negócio de novo, só confirmar que o próprio
 * Spring Security barra/libera a requisição HTTP com base nas
 * {@code GrantedAuthority} presentes. A autenticação é injetada via
 * {@link SecurityMockMvcRequestPostProcessors#authentication}, montando o
 * MESMO tipo de {@link UsernamePasswordAuthenticationToken} que
 * {@link JwtAuthenticationFilter#doFilterInternal} monta a partir de um
 * access token de verdade — sem token no header {@code Authorization},
 * {@code JwtAuthenticationFilter} não mexe no {@code SecurityContext} (ver
 * javadoc da classe), então a autenticação injetada pelo teste chega
 * intacta ao {@code @PreAuthorize}.</p>
 *
 * <p><b>CSRF:</b> {@code SecurityConfig} usa {@code csrf.spa()}, que NÃO
 * isenta nenhuma rota de negócio (só {@code /auth/**} e {@code /public/**})
 * — toda chamada de escrita aqui precisa de
 * {@link SecurityMockMvcRequestPostProcessors#csrf()}, senão o
 * {@code CsrfFilter} barra a requisição com 403 antes mesmo de chegar em
 * {@code @PreAuthorize}, mascarando o que este teste quer medir.</p>
 *
 * <p><b>Ordem @Valid vs @PreAuthorize (achado desta rodada, não um bug):</b>
 * a validação de {@code @RequestBody @Valid} roda durante a resolução dos
 * argumentos do método (antes de invocar o proxy de segurança), ou seja,
 * ANTES do {@code @PreAuthorize}. Por isso todo corpo JSON usado neste
 * teste precisa ser válido — um corpo inválido resultaria em 400 e
 * esconderia se a autorização em si funciona.</p>
 *
 * <p><b>Confirma, pela primeira vez com um teste real:</b> 401 sem
 * autenticação ({@link RestAuthenticationEntryPoint}); 403 autenticado mas
 * sem a {@code PERM_*}/role certa ({@link RestAccessDeniedHandler}); 200
 * quando a permissão bate — inclusive a distinção entre
 * {@code hasAuthority("PERM_...")} (usado pela maioria dos controllers) e
 * {@code hasRole("ADMIN")} (usado só por {@code AuditLogController}, onde
 * um {@code COORDENADOR} com todas as permissões de negócio ainda assim
 * não entra, por não ter a authority {@code ROLE_ADMIN}).</p>
 */
@AutoConfigureMockMvc
class MethodSecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VoluntarioService voluntarioService;

    @MockitoBean
    private EscalaService escalaService;

    @MockitoBean
    private InscricaoService inscricaoService;

    @MockitoBean
    private DisponibilidadeVoluntarioService disponibilidadeVoluntarioService;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private TenantService tenantService;

    @Test
    void semAutenticacaoRecebe401() throws Exception {
        mockMvc.perform(get("/voluntarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void visualizadorPodeListarCompromissos() throws Exception {
        when(voluntarioService.listarCompromissos(any())).thenReturn(List.of());

        mockMvc.perform(get("/voluntarios/{id}/commitments", UUID.randomUUID())
                        .with(comoUsuario(UsuarioTenant.Role.VISUALIZADOR)))
                .andExpect(status().isOk());
    }

    @Test
    void visualizadorLeVoluntariosMasNaoPodeEscrever() throws Exception {
        // Sem stub: VoluntarioService.buscar(...) é List<Voluntario> —
        // o comportamento padrão do Mockito para um retorno de coleção é
        // uma lista vazia (nunca null), então o controller não quebra.
        mockMvc.perform(get("/voluntarios").with(comoUsuario(UsuarioTenant.Role.VISUALIZADOR)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/voluntarios/{id}/ativo", UUID.randomUUID())
                        .param("ativo", "true")
                        .with(comoUsuario(UsuarioTenant.Role.VISUALIZADOR))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordenadorPodeEscreverEmVoluntarios() throws Exception {
        when(voluntarioService.setAtivo(any(), anyBoolean())).thenReturn(new Voluntario("Fulano de Tal"));

        mockMvc.perform(patch("/voluntarios/{id}/ativo", UUID.randomUUID())
                        .param("ativo", "true")
                        .with(comoUsuario(UsuarioTenant.Role.COORDENADOR))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void visualizadorPodeListarCandidatos() throws Exception {
        when(escalaService.listarCandidatos(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/escalas/{eventoId}/candidatos", UUID.randomUUID())
                        .param("funcao", "MISSAL")
                        .with(comoUsuario(UsuarioTenant.Role.VISUALIZADOR)))
                .andExpect(status().isOk());
    }

    @Test
    void visualizadorNaoPodeRegistrarPresenca() throws Exception {
        mockMvc.perform(patch("/escalas/vagas/{vagaId}/presenca", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"presenca\":\"PRESENTE\"}")
                        .with(comoUsuario(UsuarioTenant.Role.VISUALIZADOR))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordenadorPodeRegistrarPresenca() throws Exception {
        when(escalaService.registrarPresenca(any(), any())).thenReturn(new EscalaVaga(FuncaoEscala.CRUZ, 1));

        mockMvc.perform(patch("/escalas/vagas/{vagaId}/presenca", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"presenca\":\"PRESENTE\"}")
                        .with(comoUsuario(UsuarioTenant.Role.COORDENADOR))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void visualizadorNaoPodeAlocarVaga() throws Exception {
        mockMvc.perform(patch("/escalas/vagas/{vagaId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voluntarioId\":null}")
                        .with(comoUsuario(UsuarioTenant.Role.VISUALIZADOR))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordenadorPodeAlocarVaga() throws Exception {
        when(escalaService.alocarVaga(any(), any())).thenReturn(new EscalaVaga(FuncaoEscala.MISSAL, 1));

        mockMvc.perform(patch("/escalas/vagas/{vagaId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voluntarioId\":\"" + UUID.randomUUID() + "\"}")
                        .with(comoUsuario(UsuarioTenant.Role.COORDENADOR))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void visualizadorNaoPodeAprovarInscricao() throws Exception {
        mockMvc.perform(post("/inscricoes/{id}/aprovar", UUID.randomUUID())
                        .with(comoUsuario(UsuarioTenant.Role.VISUALIZADOR))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordenadorPodeAprovarInscricao() throws Exception {
        when(inscricaoService.aprovar(any(), any())).thenReturn(new Inscricao("Fulano"));

        mockMvc.perform(post("/inscricoes/{id}/aprovar", UUID.randomUUID())
                        .with(comoUsuario(UsuarioTenant.Role.COORDENADOR))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    /**
     * {@code AuditLogController} usa {@code hasRole("ADMIN")} — não uma das
     * permissões conceituais da seção 31 (ver javadoc da classe). Este
     * teste confirma que isso realmente é diferente de checar
     * {@code PERM_*}: um {@code COORDENADOR}, mesmo tendo TODAS as
     * permissões de negócio (voluntários/escalas/inscrições), continua sem
     * acesso — só {@code ROLE_ADMIN} entra.
     */
    @Test
    void coordenadorComTodasAsPermissoesDeNegocioNaoAcessaAuditoria() throws Exception {
        mockMvc.perform(get("/audit-log").with(comoUsuario(UsuarioTenant.Role.COORDENADOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAcessaAuditoria() throws Exception {
        mockMvc.perform(get("/audit-log").with(comoUsuario(UsuarioTenant.Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void coordenadorNaoAcessaConfiguracaoDoTenant() throws Exception {
        mockMvc.perform(get("/tenant").with(comoUsuario(UsuarioTenant.Role.COORDENADOR)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Paróquia\"}")
                        .with(comoUsuario(UsuarioTenant.Role.COORDENADOR))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPodeLerEAtualizarTenant() throws Exception {
        when(tenantService.buscarAtual()).thenReturn(new Tenant("COD", "slug", "Paróquia", Tenant.Status.ATIVO));
        when(tenantService.atualizar(any())).thenReturn(new Tenant("COD", "slug", "Paróquia", Tenant.Status.ATIVO));

        mockMvc.perform(get("/tenant").with(comoUsuario(UsuarioTenant.Role.ADMIN)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Paróquia\"}")
                        .with(comoUsuario(UsuarioTenant.Role.ADMIN))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void visualizadorLeDisponibilidadeMasNaoCria() throws Exception {
        UUID voluntarioId = UUID.randomUUID();

        mockMvc.perform(get("/voluntarios/{voluntarioId}/disponibilidades", voluntarioId)
                        .with(comoUsuario(UsuarioTenant.Role.VISUALIZADOR)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/voluntarios/{voluntarioId}/disponibilidades", voluntarioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diaSemana\":\"MONDAY\",\"periodo\":\"MANHA\"}")
                        .with(comoUsuario(UsuarioTenant.Role.VISUALIZADOR))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    /**
     * Monta a mesma {@link UsernamePasswordAuthenticationToken} que
     * {@link JwtAuthenticationFilter#doFilterInternal} monta a partir de um
     * access token de verdade — {@code ROLE_<role>} mais um
     * {@code PERM_<permissão>} por permissão de {@link RolePermissoes} (ver
     * javadoc da classe).
     */
    private RequestPostProcessor comoUsuario(UsuarioTenant.Role role) {
        AuthenticatedUser usuario = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), role);
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        for (Permissao permissao : RolePermissoes.de(role)) {
            authorities.add(new SimpleGrantedAuthority("PERM_" + permissao.name()));
        }
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(usuario, null, authorities));
    }
}
