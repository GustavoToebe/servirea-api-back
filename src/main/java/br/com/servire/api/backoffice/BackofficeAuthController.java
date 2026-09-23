package br.com.servire.api.backoffice;

import br.com.servire.api.auth.dto.LoginRequest;
import br.com.servire.api.backoffice.dto.BackofficeLoginResponse;
import br.com.servire.api.security.SecurityProperties;
import br.com.servire.api.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticação do operador (seção 111). Cookie de refresh escopado a
 * {@code /admin/auth} — o Angular do admin renova aqui, o da paróquia
 * continua em {@code /auth}. CSRF: login isento; refresh/logout
 * protegidos (dependem do cookie), mesmo padrão de {@code AuthController}.
 */
@RestController
@RequestMapping("/admin/auth")
public class BackofficeAuthController {

    static final String REFRESH_TOKEN_COOKIE = "servire_admin_refresh_token";
    private static final String COOKIE_PATH = "/admin/auth";

    private final BackofficeAuthService backofficeAuthService;
    private final SecurityProperties properties;

    public BackofficeAuthController(BackofficeAuthService backofficeAuthService, SecurityProperties properties) {
        this.backofficeAuthService = backofficeAuthService;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<BackofficeLoginResponse> login(@RequestBody @Valid LoginRequest request,
                                                          HttpServletRequest httpRequest,
                                                          HttpServletResponse httpResponse) {
        BackofficeAuthService.Tokens tokens = backofficeAuthService.login(
                request.email(), request.senha(), ip(httpRequest), userAgent(httpRequest));
        setRefreshTokenCookie(httpResponse, tokens.refreshTokenBruto());
        return ResponseEntity.ok(new BackofficeLoginResponse(tokens.accessToken(), tokens.expiresInSeconds()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<BackofficeLoginResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenBruto,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (refreshTokenBruto == null) {
            throw new UnauthorizedException("Refresh token ausente.");
        }
        BackofficeAuthService.Tokens tokens = backofficeAuthService.refresh(
                refreshTokenBruto, ip(httpRequest), userAgent(httpRequest));
        setRefreshTokenCookie(httpResponse, tokens.refreshTokenBruto());
        return ResponseEntity.ok(new BackofficeLoginResponse(tokens.accessToken(), tokens.expiresInSeconds()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenBruto,
            HttpServletResponse httpResponse) {
        if (refreshTokenBruto != null) {
            backofficeAuthService.logout(refreshTokenBruto);
        }
        clearRefreshTokenCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshTokenBruto) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshTokenBruto)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(COOKIE_PATH)
                .maxAge(properties.refreshTokenTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}
