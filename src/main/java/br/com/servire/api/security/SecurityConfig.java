package br.com.servire.api.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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
 * do navegador (o corpo da requisição é autossuficiente). Já
 * {@code /auth/refresh} e {@code /auth/logout} dependem do cookie
 * HttpOnly do refresh token (seção 92) e por isso continuam protegidos —
 * ver a javadoc de {@code AuthController}.</p>
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
    private static final String[] ROTAS_PUBLICAS = {"/auth/**", "/public/**", "/actuator/health", "/error"};

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
                                                     RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .spa()
                        .ignoringRequestMatchers(
                                "/auth/login", "/auth/select-tenant",
                                "/auth/forgot-password", "/auth/reset-password",
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
}
