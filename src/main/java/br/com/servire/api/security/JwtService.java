package br.com.servire.api.security;

import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.web.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Emissão e validação de JWT (seção 33 do plano mestre). Usa
 * {@code io.jsonwebtoken} (JJWT 0.13.0, pesquisado e confirmado como a
 * versão mais recente em 21/09/2026 — o BOM do Spring Boot não gerencia
 * essa biblioteca, então a versão é fixada explicitamente no
 * {@code pom.xml}), com a API fluente introduzida na série 0.12.x
 * ({@code Jwts.builder()...signWith(key)}, {@code Jwts.parser()
 * .verifyWith(key).build().parseSignedClaims(token)}), confirmada contra a
 * documentação oficial do projeto antes de escrever este código.
 *
 * <p>Dois tipos de token, distinguidos pelo claim customizado
 * {@code purpose} — nunca aceitar um pelo outro:</p>
 * <ul>
 *   <li><b>access</b> — token de acesso normal (seção 34: 15 minutos),
 *   carrega {@code sub} (usuarioId), {@code tenant} (tenantId) e
 *   {@code roles} (seção 33 usa esse formato de array, mesmo hoje só
 *   existindo uma role por vínculo usuario_tenant).</li>
 *   <li><b>tenant_selection</b> — token temporário (poucos minutos),
 *   emitido só quando o usuário tem mais de uma paróquia (seção 30: fluxo
 *   de {@code POST /auth/select-tenant}), carrega só {@code sub} — sem
 *   claim de tenant, porque o tenant ainda não foi escolhido.</li>
 * </ul>
 *
 * <p>O backend não confia apenas nos claims (seção 33): o
 * {@code JwtAuthenticationFilter} revalida usuário/tenant/vínculo no banco
 * a cada requisição (Kill Switch, seção 28) — este serviço só garante que
 * o token não foi adulterado e ainda não expirou.</p>
 */
@Service
public class JwtService {

    static final String CLAIM_PURPOSE = "purpose";
    static final String PURPOSE_ACCESS = "access";
    static final String PURPOSE_TENANT_SELECTION = "tenant_selection";
    static final String CLAIM_TENANT = "tenant";
    static final String CLAIM_ROLES = "roles";

    private final SecretKey key;
    private final SecurityProperties.Jwt config;

    public JwtService(SecurityProperties properties) {
        this.config = properties.jwt();
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(config.secret());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "servire.security.jwt.secret precisa ser uma string Base64 válida.", e);
        }
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "servire.security.jwt.secret precisa decodificar para pelo menos 32 bytes "
                            + "(256 bits, exigido pelo HMAC-SHA256) — atual: " + secretBytes.length + " bytes.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String gerarAccessToken(UUID usuarioId, UUID tenantId, UsuarioTenant.Role role) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim(CLAIM_TENANT, tenantId.toString())
                .claim(CLAIM_ROLES, List.of(role.name()))
                .claim(CLAIM_PURPOSE, PURPOSE_ACCESS)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(config.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    public String gerarTokenSelecaoTenant(UUID usuarioId) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim(CLAIM_PURPOSE, PURPOSE_TENANT_SELECTION)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(config.tenantSelectionTokenTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * @throws UnauthorizedException se o token for inválido, expirado, de
     * outra finalidade (ex.: um token de seleção de tenant apresentado
     * aqui), ou com claims incompletas/incoerentes.
     */
    public AccessTokenClaims validarAccessToken(String token) {
        Claims claims = parseClaims(token);
        exigirPurpose(claims, PURPOSE_ACCESS);
        UUID usuarioId = parseUuidClaim(claims.getSubject(), "sub");

        Object tenantRaw = claims.get(CLAIM_TENANT);
        if (tenantRaw == null) {
            throw new UnauthorizedException("Token de acesso sem claim de tenant.");
        }
        UUID tenantId = parseUuidClaim(tenantRaw.toString(), CLAIM_TENANT);

        List<?> rolesRaw = claims.get(CLAIM_ROLES, List.class);
        if (rolesRaw == null || rolesRaw.isEmpty()) {
            throw new UnauthorizedException("Token de acesso sem claim de role.");
        }
        UsuarioTenant.Role role;
        try {
            role = UsuarioTenant.Role.valueOf(rolesRaw.get(0).toString());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Token de acesso com role desconhecida.");
        }

        return new AccessTokenClaims(usuarioId, tenantId, role);
    }

    /**
     * @throws UnauthorizedException se o token for inválido, expirado ou de
     * outra finalidade (ex.: um access token apresentado aqui).
     */
    public UUID validarTokenSelecaoTenant(String token) {
        Claims claims = parseClaims(token);
        exigirPurpose(claims, PURPOSE_TENANT_SELECTION);
        return parseUuidClaim(claims.getSubject(), "sub");
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Token inválido ou expirado.");
        }
    }

    private void exigirPurpose(Claims claims, String esperado) {
        Object purpose = claims.get(CLAIM_PURPOSE);
        if (!esperado.equals(purpose)) {
            throw new UnauthorizedException("Token usado para uma finalidade diferente da esperada.");
        }
    }

    private UUID parseUuidClaim(String raw, String claimName) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Token com claim '" + claimName + "' inválida.");
        }
    }

    public record AccessTokenClaims(UUID usuarioId, UUID tenantId, UsuarioTenant.Role role) {
    }
}
