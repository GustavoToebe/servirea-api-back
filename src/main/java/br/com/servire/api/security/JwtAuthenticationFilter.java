package br.com.servire.api.security;

import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Substitui o {@code DevFixedTenantFilter} da Fase 4 (removido nesta
 * fase — seção 104 do plano mestre: "Depois passa a vir do JWT").
 *
 * <p>Extrai o access token do header {@code Authorization: Bearer
 * <token>}, valida assinatura/expiração/finalidade via {@link JwtService}
 * e então REVALIDA o Kill Switch (seção 28) direto no banco a cada
 * requisição — usuário ativo, vínculo {@code usuario_tenant} ATIVO, tenant
 * ATIVO/TRIAL. Isso é deliberado: os claims do JWT ficam desatualizados
 * até o token expirar (15 minutos, seção 34) — sem essa revalidação a
 * cada requisição, bloquear um usuário ou uma paróquia não teria efeito
 * imediato, só depois do access token expirar.</p>
 *
 * <p>Quando o token está ausente, é inválido, ou alguma checagem do Kill
 * Switch falha, este filtro simplesmente NÃO autentica a requisição (não
 * lança exceção) — deixa o {@code SecurityContext} vazio e segue a
 * cadeia normalmente. Cabe a {@code authorizeHttpRequests}/
 * {@link RestAuthenticationEntryPoint} (configurados em
 * {@link SecurityConfig}) decidir se o endpoint exige autenticação — as
 * rotas {@code /auth/**} são {@code permitAll} de propósito, então
 * requisições sem token continuam funcionando normalmente nelas.</p>
 *
 * <p>Segue a mesma regra crítica do {@link TenantContext} herdada do
 * antigo {@code DevFixedTenantFilter}: {@code set} sempre pareado com
 * {@code clear} em {@code finally}, mesmo em caso de exceção - nunca
 * deixar o contexto vazar para a próxima requisição atendida pela mesma
 * thread (seção 20/78/79/80). O {@code SecurityContextHolder} em si não
 * precisa de limpeza manual aqui: o {@code SecurityContextHolderFilter}
 * do Spring Security já limpa em {@code finally} ao redor de toda a
 * cadeia, mesmo em modo {@code STATELESS}.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /** Mesma chave de MDC já usada pelo antigo {@code DevFixedTenantFilter} (seção 65: campo "tenantId" nos logs). */
    public static final String MDC_KEY = "tenantId";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioTenantRepository usuarioTenantRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                    UsuarioRepository usuarioRepository,
                                    UsuarioTenantRepository usuarioTenantRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.usuarioTenantRepository = usuarioTenantRepository;
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
        TenantContext.set(usuario.tenantId());
        MDC.put(MDC_KEY, usuario.tenantId().toString());
        try {
            List<GrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority("ROLE_" + usuario.role().name()));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(usuario, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            TenantContext.clear();
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

        JwtService.AccessTokenClaims claims;
        try {
            claims = jwtService.validarAccessToken(token);
        } catch (RuntimeException e) {
            // Token ausente/inválido/expirado/de outra finalidade - tratado
            // como requisição anônima, nunca propagado a partir daqui (ver
            // javadoc da classe).
            return Optional.empty();
        }

        Usuario usuarioEntidade = usuarioRepository.findById(claims.usuarioId()).orElse(null);
        if (usuarioEntidade == null || !usuarioEntidade.isAtivo()) {
            return Optional.empty();
        }

        Optional<UsuarioTenant> vinculo =
                usuarioTenantRepository.findByUsuario_IdAndTenant_Id(claims.usuarioId(), claims.tenantId());
        if (vinculo.isEmpty() || vinculo.get().getStatus() != UsuarioTenant.Status.ATIVO) {
            return Optional.empty();
        }

        Tenant tenant = vinculo.get().getTenant();
        if (tenant.getStatus() != Tenant.Status.ATIVO && tenant.getStatus() != Tenant.Status.TRIAL) {
            return Optional.empty();
        }

        return Optional.of(new AuthenticatedUser(claims.usuarioId(), claims.tenantId(), claims.role()));
    }
}
