package br.com.servire.api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Set;

/**
 * Configuração de segurança da Fase 5 (seção 32/105 do plano mestre).
 * Substitui por completo a segurança padrão que o
 * {@code spring-boot-starter-security} ativaria sozinho (login gerado com
 * senha aleatória no log) — definir um bean {@link SecurityFilterChain}
 * próprio desliga esse auto-configure padrão do Spring Boot.
 *
 * <p><b>CSRF (seção 91):</b> {@code csrf.spa()} é o método de conveniência
 * do Spring Security (confirmado contra a documentação oficial em
 * 21/09/2026) pensado exatamente para SPA - cuida sozinho do
 * armazenamento do token CSRF em cookie legível por JavaScript
 * ({@code XSRF-TOKEN}, convenção que o Angular já lê nativamente),
 * proteção contra BREACH, e renovação do token após login/logout.
 * {@code /auth/login}, {@code /auth/select-tenant},
 * {@code /auth/forgot-password} e {@code /auth/reset-password} são
 * isentos de CSRF porque nenhum deles depende de uma credencial ambiente
 * do navegador (o corpo da requisição é autossuficiente). O mesmo vale
 * para {@code /admin/auth/login} (seção 111). Já
 * {@code /auth/refresh}, {@code /auth/logout}, {@code /admin/auth/refresh}
 * e {@code /admin/auth/logout} dependem do cookie HttpOnly do refresh
 * token (seção 92) e por isso continuam protegidos —
 * ver a javadoc de {@code AuthController}.</p>
 *
 * <p><b>CSRF x Bearer (correção de 24/09/2026):</b> fora de
 * {@code /auth/**} e {@code /admin/auth/**}, requisição com
 * {@code Authorization: Bearer} não exige token CSRF — o access token
 * mora em {@code sessionStorage} e o navegador nunca o envia sozinho,
 * então não há credencial ambiente para forjar (um site de terceiro não
 * consegue pôr esse header sem passar pelo preflight do CORS). Antes todo
 * POST/PUT/DELETE de negócio exigia {@code X-XSRF-TOKEN}, e o Angular não
 * manda esse header para URL absoluta (a API fica em outra origem):
 * salvar pessoa ou aprovar inscrição voltava 403. As rotas do cookie de
 * refresh continuam exigindo o token mesmo com Bearer.</p>
 *
 * <p><b>CORS:</b> só libera as origens vindas de
 * {@code servire.security.cors.allowed-origins} (nunca {@code *} — seção
 * 91), com credenciais habilitadas (necessário para o navegador enviar o
 * cookie do refresh token em requisições cross-site ao domínio da API).</p>
 *
 * <p><b>Sessão stateless:</b> nenhuma sessão HTTP é criada ou usada -
 * toda autenticação é resolvida a cada requisição por
 * {@link JwtAuthenticationFilter}, a partir do access token.</p>
 *
 * <p><b>Roles e permissões (seção 31, Fase 11):</b>
 * {@code @EnableMethodSecurity} liga o suporte a {@code @PreAuthorize} nos
 * métodos dos controllers — {@code JwtAuthenticationFilter} concede tanto
 * {@code ROLE_<role>} quanto um {@code PERM_<permissão>} por permissão
 * mapeada em {@link RolePermissoes}; os controllers checam só a permissão
 * (ex.: {@code @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_WRITE')")}),
 * nunca a role diretamente. Uma {@code AccessDeniedException} lançada por
 * {@code @PreAuthorize} acaba tratada pelo MESMO {@link RestAccessDeniedHandler}
 * já configurado abaixo para {@code authorizeHttpRequests} — mas isso NÃO
 * é automático (correção de 22/09/2026, ver Bug real #13 no README.md):
 * como essa exceção é lançada de DENTRO da invocação do método do
 * controller (pelo proxy AOP de {@code @EnableMethodSecurity}), o
 * {@code @ExceptionHandler(Exception.class)} genérico de
 * {@code GlobalExceptionHandler} a capturava PRIMEIRO (roda dentro de
 * {@code DispatcherServlet.doDispatch()}, antes da exceção conseguir
 * escapar para a cadeia de filtros onde o {@code ExceptionTranslationFilter}
 * vive) — só chega de fato no {@code ExceptionTranslationFilter} porque
 * {@code GlobalExceptionHandler} tem handlers explícitos para
 * {@code AccessDeniedException}/{@code AuthenticationException} que
 * apenas relançam a exceção. Ver javadoc de
 * {@code GlobalExceptionHandler#handleAccessDenied} para o detalhe
 * completo do mecanismo.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    // /error precisa ser público: se uma exceção estoura na cadeia de
    // filtros, o Boot despacha para cá SEM reaplicar o JWT
    // (OncePerRequestFilter pula ERROR dispatch). Sem isto, o cliente
    // vê 401 path=/error em vez do 500 real (Bug real #15).
    // /admin/auth/login é o equivalente do /auth/login para o operador
    // (seção 111) — o JWT ainda não existe nesse momento.
    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "TRACE", "OPTIONS");

    private static final String[] ROTAS_PUBLICAS = {
            "/auth/**", "/admin/auth/**", "/public/**", "/actuator/health", "/error"};

    @Bean
    public PasswordEncoder passwordEncoder() {
        // DelegatingPasswordEncoder com BCrypt como algoritmo padrão de
        // codificação (o prefixo "{bcrypt}" gravado junto do hash permite
        // trocar de algoritmo no futuro sem invalidar senhas já
        // cadastradas) - confirmado como o padrão oficialmente recomendado
        // pela documentação do Spring Security em 21/09/2026.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     JwtAuthenticationFilter jwtAuthenticationFilter,
                                                     CorsConfigurationSource corsConfigurationSource,
                                                     RestAuthenticationEntryPoint authenticationEntryPoint,
                                                     RestAccessDeniedHandler accessDeniedHandler,
                                                     SecurityProperties properties) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .spa()
                        .csrfTokenRepository(csrfTokenRepository(properties))
                        .requireCsrfProtectionMatcher(SecurityConfig::exigeCsrf)
                        .ignoringRequestMatchers(
                                "/auth/login", "/auth/select-tenant",
                                "/auth/forgot-password", "/auth/reset-password",
                                "/admin/auth/login",
                                "/public/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ROTAS_PUBLICAS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Mesmo repositório do {@code spa()} ({@code XSRF-TOKEN} legível por JS), com domínio configurável. */
    static CookieCsrfTokenRepository csrfTokenRepository(SecurityProperties properties) {
        CookieCsrfTokenRepository repositorio = CookieCsrfTokenRepository.withHttpOnlyFalse();
        String dominio = properties.csrf() == null ? null : properties.csrf().cookieDomain();
        if (dominio != null && !dominio.isBlank()) {
            repositorio.setCookieCustomizer(cookie -> cookie.domain(dominio.trim()));
        }
        return repositorio;
    }

    /** Ver "CSRF x Bearer" na javadoc da classe. */
    static boolean exigeCsrf(HttpServletRequest request) {
        if (METODOS_SEGUROS.contains(request.getMethod())) {
            return false;
        }
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        if (caminho.startsWith("/auth/") || caminho.startsWith("/admin/auth/")) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7);
    }
}
