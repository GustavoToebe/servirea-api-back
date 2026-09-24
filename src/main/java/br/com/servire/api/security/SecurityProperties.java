package br.com.servire.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuração da Fase 5 (autenticação própria, seção 32/105 do plano
 * mestre), lida do prefixo {@code servire.security} em
 * {@code application*.yml}.
 *
 * <p>{@code jwt.secret} nunca tem valor padrão no {@code application.yml}
 * base — só em {@code application-dev.yml}/{@code application-test.yml}
 * (segredo fixo, só para desenvolvimento/teste). Em produção
 * ({@code application-prod.yml}) é obrigatório vir de
 * {@code JWT_SECRET}, sem valor padrão — mesma regra já aplicada a
 * {@code DB_URL}/{@code DB_USER}/{@code DB_PASSWORD} (seção 90: nunca
 * versionar segredo). Precisa ser uma string Base64 de pelo menos 32
 * bytes decodificados (256 bits) — HMAC-SHA256 exige isso; um segredo
 * menor faz o {@code JwtService} falhar ao montar a chave, de propósito
 * (falhar alto e cedo em vez de assinar tokens com uma chave fraca).</p>
 */
@ConfigurationProperties(prefix = "servire.security")
public record SecurityProperties(Jwt jwt, Duration refreshTokenTtl, Duration passwordResetTokenTtl, Cors cors,
                                 Csrf csrf) {

    public record Jwt(String secret, Duration accessTokenTtl, Duration tenantSelectionTokenTtl) {
    }

    /**
     * {@code allowedOrigins} vazio/ausente por padrão (seção 91: nunca usar
     * {@code Access-Control-Allow-Origin: *} em endpoint autenticado) — o
     * CORS só libera as origens explicitamente configuradas via
     * {@code CORS_ALLOWED_ORIGINS} (lista separada por vírgula).
     */
    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * {@code cookieDomain}: domínio do cookie {@code XSRF-TOKEN}. Vazio =
     * cookie só do host da API (dev: {@code localhost} vale para todas as
     * portas). Em produção a API ({@code api.servirea.com.br}) e o front
     * ficam em hosts diferentes, e o JavaScript do front só lê o cookie se
     * ele for do domínio pai ({@code servirea.com.br}) — ver
     * {@code CSRF_COOKIE_DOMAIN} em {@code application-prod.yml}.
     */
    public record Csrf(String cookieDomain) {
    }
}
