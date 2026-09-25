package br.com.servire.api.security;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.diocese.DioceseService;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.acesso.Perfil;
import br.com.servire.api.acesso.PermissoesDaSessao;
import br.com.servire.api.escala.EscalaService;
import br.com.servire.api.escala.EscalaVaga;
import br.com.servire.api.inscricao.Inscricao;
import br.com.servire.api.inscricao.InscricaoService;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaPapel;
import br.com.servire.api.pessoa.PessoaService;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * <p><b>CSRF:</b> desde 24/09/2026 {@code SecurityConfig} só dispensa o
 * token CSRF quando a requisição traz {@code Authorization: Bearer}. Este
 * teste injeta a autenticação direto (sem header), então toda chamada de
 * escrita aqui continua precisando de
 * {@link SecurityMockMvcRequestPostProcessors#csrf()}, senão o
 * {@code CsrfFilter} barra a requisição com 403 antes mesmo de chegar em
 * {@code @PreAuthorize}, mascarando o que este teste quer medir. O fluxo
 * com Bearer de verdade está em {@code FluxoHttpIntegrationTest}.</p>
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
    private PessoaService pessoaService;

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

    @MockitoBean
    private DioceseService dioceseService;

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
        Pessoa pessoa = new Pessoa(PessoaPapel.VOLUNTARIO, "Fulano de Tal");
        Voluntario voluntario = new Voluntario();
        pessoa.setVoluntario(voluntario);
        when(voluntarioService.setAtivo(any(), anyBoolean())).thenReturn(voluntario);

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

        mockMvc.perform(get("/dioceses").with(comoUsuario(UsuarioTenant.Role.COORDENADOR)))
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

        when(dioceseService.listarEmUso()).thenReturn(List.of());
        mockMvc.perform(get("/dioceses").with(comoUsuario(UsuarioTenant.Role.ADMIN)))
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
     * A matriz do perfil vale por ação (25/09/2026). Antes qualquer ação do
     * módulo virava {@code ESCALA_WRITE} e quem só registrava presença
     * excluía escala.
     */
    @Test
    void perfilSoComPresencaRegistraPresencaMasNaoExcluiNemCriaEscala() throws Exception {
        when(escalaService.registrarPresenca(any(), any())).thenReturn(new EscalaVaga(FuncaoEscala.CRUZ, 1));
        RequestPostProcessor soPresenca = comoPerfil("ESCALA", "VAGA", "VAGA_PRESENCA");

        mockMvc.perform(patch("/escalas/vagas/{vagaId}/presenca", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"presenca\":\"PRESENTE\"}")
                        .with(soPresenca)
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/escalas/{id}", UUID.randomUUID()).with(soPresenca)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/escalas/vagas/{vagaId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voluntarioId\":null}")
                        .with(soPresenca)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void perfilQueAprovaInscricaoNaoRejeita() throws Exception {
        when(inscricaoService.aprovar(any(), any())).thenReturn(new Inscricao("Fulano"));
        RequestPostProcessor soAprova = comoPerfil("INSCRICAO", "INSCRICAO_APROVAR");

        mockMvc.perform(post("/inscricoes/{id}/aprovar", UUID.randomUUID()).with(soAprova)
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/inscricoes/{id}/rejeitar", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"x\"}")
                        .with(soAprova)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void perfilQueSoAlteraPessoaNaoAtivaNemInativa() throws Exception {
        mockMvc.perform(patch("/voluntarios/{id}/ativo", UUID.randomUUID())
                        .param("ativo", "false")
                        .with(comoPerfil("PESSOA", "PESSOA_ALTERAR"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void integracaoSemAssinaturaRecebe401() throws Exception {
        mockMvc.perform(get("/integracao/v1/instancias/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Caminho que o Spring decodifica ao escolher o controller, mas que não
     * começa com {@code /integracao/} cru. Mesmo que escapasse do filtro
     * HMAC, a rota exige {@code PERM_INTEGRACAO} e não é pública.
     */
    @Test
    void integracaoComCaminhoCodificadoNaoPulaOHmac() throws Exception {
        mockMvc.perform(get(URI.create("/%69ntegracao/v1/instancias/" + UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioDaParoquiaNaoAcessaIntegracao() throws Exception {
        mockMvc.perform(get("/integracao/v1/instancias/{id}", UUID.randomUUID())
                        .with(comoUsuario(UsuarioTenant.Role.ADMIN)))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor comoPerfil(String... codigos) {
        Perfil perfil = new Perfil(UUID.randomUUID(), "Teste", false, false);
        perfil.substituirPermissoes(List.of(codigos));
        AuthenticatedUser usuario = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UsuarioTenant.Role.VISUALIZADOR);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(usuario, null, PermissoesDaSessao.doPerfil(perfil)));
    }

    /**
     * Monta a mesma {@link UsernamePasswordAuthenticationToken} que
     * {@link JwtAuthenticationFilter#doFilterInternal} monta a partir de um
     * access token de verdade para um vínculo sem perfil
     * ({@link PermissoesDaSessao#daRole}).
     */
    private RequestPostProcessor comoUsuario(UsuarioTenant.Role role) {
        AuthenticatedUser usuario = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), role);
        List<GrantedAuthority> authorities = PermissoesDaSessao.daRole(role);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(usuario, null, authorities));
    }
}
