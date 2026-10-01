package br.com.servire.api.auth;

import br.com.servire.api.auth.dto.AccessTokenResponse;
import br.com.servire.api.auth.dto.ForgotPasswordRequest;
import br.com.servire.api.auth.dto.LoginRequest;
import br.com.servire.api.auth.dto.LoginResponse;
import br.com.servire.api.auth.dto.RefreshRequest;
import br.com.servire.api.auth.dto.ResetPasswordRequest;
import br.com.servire.api.auth.dto.SelectTenantRequest;
import br.com.servire.api.security.SecurityProperties;
import br.com.servire.api.web.ClientIp;
import br.com.servire.api.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticação (seção 32 do plano mestre). Todos liberados
 * sem JWT em {@code SecurityConfig} ({@code /auth/**}) — faz sentido, é
 * aqui que o JWT é obtido.
 *
 * <p>O refresh token nunca aparece no corpo de nenhuma resposta — só num
 * cookie HttpOnly+Secure+SameSite=None, escopado a {@code /auth} (seção
 * 92, decisão tomada com o usuário em 21/09/2026 ao iniciar esta fase). Em
 * produção a API fica em {@code /api} do domínio do app e o Caddy tira o prefixo,
 * então o caminho do cookie é {@code /api/auth}: {@code servire.security.refresh-cookie-path}.
 * {@code /auth/refresh} e {@code /auth/logout} são os únicos endpoints que
 * dependem desse cookie — por isso são os únicos que continuam protegidos
 * por CSRF em {@code SecurityConfig} (os demais não dependem de nenhuma
 * credencial ambiente do navegador, então CSRF não se aplica a eles).</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    static final String REFRESH_TOKEN_COOKIE = "servire_refresh_token";

    private final AuthService authService;
    private final LimiteLogin limiteLogin;
    private final SecurityProperties properties;
    private final String refreshCookiePath;

    public AuthController(AuthService authService, SecurityProperties properties, LimiteLogin limiteLogin,
                          @Value("${servire.security.refresh-cookie-path:/auth}") String refreshCookiePath) {
        this.authService = authService;
        this.limiteLogin = limiteLogin;
        this.properties = properties;
        this.refreshCookiePath = refreshCookiePath;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request,
                                                HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        try {limiteLogin.registrar(ip(httpRequest),request.email());}
        catch(LimiteLoginException ex){httpResponse.setHeader("Retry-After",Long.toString(ex.segundos()));throw ex;}
        AuthService.LoginResultado resultado = authService.login(
                request.email(), request.senha(), ip(httpRequest), userAgent(httpRequest));

        if (resultado.precisaSelecionarTenant()) {
            return ResponseEntity.ok(LoginResponse.pendenteSelecao(
                    resultado.tokenSelecaoTenant(), resultado.tenantsDisponiveis()));
        }

        AuthService.TokensCompletos tokens = resultado.completo();
        setRefreshTokenCookie(httpResponse, tokens.refreshTokenBruto());
        return ResponseEntity.ok(LoginResponse.completo(
                tokens.accessToken(), tokens.expiresInSeconds(), tokens.tenantAtual()));
    }

    @PostMapping("/select-tenant")
    public ResponseEntity<AccessTokenResponse> selectTenant(@RequestBody @Valid SelectTenantRequest request,
                                                             HttpServletRequest httpRequest,
                                                             HttpServletResponse httpResponse) {
        AuthService.TokensCompletos tokens = authService.selecionarTenant(
                request.tokenSelecaoTenant(), request.tenantId(), ip(httpRequest), userAgent(httpRequest));
        setRefreshTokenCookie(httpResponse, tokens.refreshTokenBruto());
        return ResponseEntity.ok(new AccessTokenResponse(
                tokens.accessToken(), tokens.expiresInSeconds(), tokens.tenantAtual()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @RequestBody @Valid RefreshRequest request,
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenBruto,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (refreshTokenBruto == null) {
            throw new UnauthorizedException("Refresh token ausente.");
        }
        AuthService.TokensCompletos tokens = authService.refresh(
                refreshTokenBruto, request.tenantId(), ip(httpRequest), userAgent(httpRequest));
        setRefreshTokenCookie(httpResponse, tokens.refreshTokenBruto());
        return ResponseEntity.ok(new AccessTokenResponse(
                tokens.accessToken(), tokens.expiresInSeconds(), tokens.tenantAtual()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenBruto,
            HttpServletResponse httpResponse) {
        if (refreshTokenBruto != null) {
            authService.logout(refreshTokenBruto);
        }
        clearRefreshTokenCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        authService.esqueciSenha(request.email());
        // Sempre 202, exista ou não o e-mail - nunca revelar (evita
        // enumeração de e-mails cadastrados).
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        authService.redefinirSenha(request.token(), request.novaSenha());
        return ResponseEntity.noContent().build();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshTokenBruto) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshTokenBruto)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(refreshCookiePath)
                .maxAge(properties.refreshTokenTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(refreshCookiePath)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // Em prod o Tomcat RemoteIpValve reescreve getRemoteAddr() quando o
    // hop é um proxy interno. Não ler X-Forwarded-For aqui — ver ClientIp.
    private String ip(HttpServletRequest request) {
        return ClientIp.de(request);
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}
