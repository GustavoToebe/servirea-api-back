package br.com.servire.api.security;

import br.com.servire.api.acesso.PermissoesDaSessao;
import br.com.servire.api.integracao.AcessoParoquia;
import br.com.servire.api.integracao.SuporteSessao;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Substitui o {@code DevFixedTenantFilter} da Fase 4 (removido nesta
 * fase — seção 104 do plano mestre: "Depois passa a vir do JWT").
 *
 * <p>Extrai o token do header {@code Authorization: Bearer
 * <token>}, valida assinatura/expiração/finalidade via {@link JwtService}
 * e então REVALIDA o Kill Switch (seção 28) direto no banco a cada
 * requisição. Dois ramos:</p>
 * <ul>
 *   <li>{@code purpose=access} — usuário ativo, vínculo
 *   {@code usuario_tenant} ATIVO, perfil ativo e paróquia liberada
 *   ({@link AcessoParoquia}: cópia local dos direitos + 72h).</li>
 *   <li>{@code purpose=suporte_app} — código de uso único pedido pela
 *   Central e trocado em {@code /auth/suporte/trocar}; tenant precisa
 *   existir (aceita paróquia bloqueada, senão o dono não atende quem está
 *   inadimplente); SEM vínculo {@code usuario_tenant}.</li>
 * </ul>
 *
 * <p>O token {@code purpose=backoffice} e o access token com
 * {@code suporte=true} do operador saíram em 25/09/2026, junto com o
 * painel: o operador agora mora na Central.</p>
 *
 * <p>Quando o token está ausente, é inválido, ou alguma checagem do Kill
 * Switch falha, este filtro simplesmente NÃO autentica a requisição (não
 * lança exceção) — deixa o {@code SecurityContext} vazio e segue a
 * cadeia normalmente. Cabe a {@code authorizeHttpRequests}/
 * {@link RestAuthenticationEntryPoint} (configurados em
 * {@link SecurityConfig}) decidir se o endpoint exige autenticação — as
 * rotas {@code /auth/**} e {@code /admin/auth/login} são {@code permitAll}
 * de propósito.</p>
 *
 * <p>Segue a mesma regra crítica do {@link TenantContext}: {@code set}
 * sempre pareado com {@code clear} em {@code finally}, mesmo em caso de
 * exceção - nunca deixar o contexto vazar para a próxima requisição
 * atendida pela mesma thread (seção 20/78/79/80). O
 * {@code SecurityContextHolder} em si não precisa de limpeza manual
 * aqui: o {@code SecurityContextHolderFilter} do Spring Security já
 * limpa em {@code finally} ao redor de toda a cadeia, mesmo em modo
 * {@code STATELESS}.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /** Mesma chave de MDC já usada pelo antigo {@code DevFixedTenantFilter} (seção 65: campo "tenantId" nos logs). */
    public static final String MDC_KEY = "tenantId";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioTenantRepository usuarioTenantRepository;
    private final TenantRepository tenantRepository;
    private final AcessoParoquia acessoParoquia;

    public JwtAuthenticationFilter(JwtService jwtService,
                                    UsuarioRepository usuarioRepository,
                                    UsuarioTenantRepository usuarioTenantRepository,
                                    TenantRepository tenantRepository,
                                    AcessoParoquia acessoParoquia) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.usuarioTenantRepository = usuarioTenantRepository;
        this.tenantRepository = tenantRepository;
        this.acessoParoquia = acessoParoquia;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<AuthenticatedUser> autenticado = autenticar(request);
        if (autenticado.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthenticatedUser usuario = autenticado.get();
        if (usuario.tenantId() != null) {
            TenantContext.set(usuario.tenantId());
            MDC.put(MDC_KEY, usuario.tenantId().toString());
        }
        try {
            List<GrantedAuthority> authorities = autoridadesDe(usuario);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(usuario, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            TenantContext.clear();
            SuporteSessao.clear();
        }
    }

    /**
     * @return o usuário autenticado, ou {@link Optional#empty()} se não
     * havia token, o token era inválido/expirado, ou alguma checagem do
     * Kill Switch falhou — em nenhum desses casos uma exceção é lançada
     * (ver javadoc da classe).
     */
    private Optional<AuthenticatedUser> autenticar(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length());

        String purpose;
        try {
            purpose = jwtService.purpose(token);
        } catch (RuntimeException e) {
            return Optional.empty();
        }

        if (JwtService.PURPOSE_SUPORTE_APP.equals(purpose)) {
            return autenticarSuporteApp(token);
        }
        if (JwtService.PURPOSE_ACCESS.equals(purpose)) {
            return autenticarAccess(token);
        }
        return Optional.empty();
    }

    private Optional<AuthenticatedUser> autenticarAccess(String token) {
        JwtService.AccessTokenClaims claims;
        try {
            claims = jwtService.validarAccessToken(token);
        } catch (RuntimeException e) {
            return Optional.empty();
        }

        Usuario usuarioEntidade = usuarioRepository.findById(claims.usuarioId()).orElse(null);
        if (usuarioEntidade == null || !usuarioEntidade.isAtivo()) {
            return Optional.empty();
        }

        if (claims.suporte()) {
            // Suporte do operador pelo painel antigo; hoje só suporte_app.
            return Optional.empty();
        }

        Optional<UsuarioTenant> vinculo =
                usuarioTenantRepository.findComPerfilByUsuario_IdAndTenant_Id(claims.usuarioId(), claims.tenantId());
        if (vinculo.isEmpty() || vinculo.get().getStatus() != UsuarioTenant.Status.ATIVO) {
            return Optional.empty();
        }
        if (vinculo.get().getPerfil() != null && !vinculo.get().getPerfil().isAtivo()) {
            return Optional.empty();
        }

        // tenant e perfil precisam já vir inicializados pelo repositório
        // (EntityGraph) — este filtro não é @Transactional e open-in-view
        // está desligado.
        Tenant tenant = vinculo.get().getTenant();
        if (!acessoParoquia.liberada(tenant)) {
            return Optional.empty();
        }

        return Optional.of(new AuthenticatedUser(claims.usuarioId(), claims.tenantId(), claims.role()));
    }

    /** Código de uso único da Central. Aceita paróquia bloqueada. */
    private Optional<AuthenticatedUser> autenticarSuporteApp(String token) {
        JwtService.SuporteAppClaims claims;
        try {
            claims = jwtService.validarTokenSuporte(token);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (!tenantRepository.existsById(claims.tenantId())) {
            return Optional.empty();
        }
        SuporteSessao.set(new SuporteSessao.Dados(claims.operadorNome(), claims.operadorEmail(), claims.motivo()));
        return Optional.of(AuthenticatedUser.suporte(claims.codigoId(), claims.tenantId()));
    }

    /**
     * Monta as {@link GrantedAuthority} do usuário autenticado a partir do
     * perfil da paróquia ({@link PermissoesDaSessao}). Suporte recebe o
     * conjunto do acesso total.
     */
    private List<GrantedAuthority> autoridadesDe(AuthenticatedUser usuario) {
        if (usuario.suporte()) {
            return PermissoesDaSessao.acessoTotal();
        }
        return usuarioTenantRepository
                .findComPerfilByUsuario_IdAndTenant_Id(usuario.usuarioId(), usuario.tenantId())
                .map(PermissoesDaSessao::de)
                .orElseGet(() -> PermissoesDaSessao.daRole(usuario.role()));
    }
}
