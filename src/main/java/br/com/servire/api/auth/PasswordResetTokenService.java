package br.com.servire.api.auth;

import br.com.servire.api.security.OpaqueTokenGenerator;
import br.com.servire.api.security.SecurityProperties;
import br.com.servire.api.web.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Tokens de "esqueci minha senha" (seção 32 do plano mestre). Algoritmo de
 * geração/hash em {@link OpaqueTokenGenerator} (mesmo usado por
 * {@link RefreshTokenService} — ver a javadoc de lá para o porquê de
 * SHA-256 em vez de bcrypt/argon2).
 */
@Service
public class PasswordResetTokenService {

    private final PasswordResetTokenRepository repository;
    private final SecurityProperties properties;

    public PasswordResetTokenService(PasswordResetTokenRepository repository, SecurityProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public String gerar(Usuario usuario) {
        String tokenBruto = OpaqueTokenGenerator.gerar();
        PasswordResetToken entidade = new PasswordResetToken(
                usuario, OpaqueTokenGenerator.hash(tokenBruto),
                Instant.now().plus(properties.passwordResetTokenTtl()));
        repository.save(entidade);
        return tokenBruto;
    }

    @Transactional
    public String gerarConvite(Usuario usuario) {
        String tokenBruto = gerar(usuario);
        repository.findByTokenHash(OpaqueTokenGenerator.hash(tokenBruto)).ifPresent(token -> {
            token.setFinalidade("CONVITE");
            repository.save(token);
        });
        return tokenBruto;
    }

    /**
     * Valida e consome (marca como usado) o token — uso único: uma
     * segunda tentativa com o mesmo token, mesmo dentro da validade,
     * falha.
     *
     * @throws UnauthorizedException se o token for desconhecido, já usado
     * ou expirado.
     */
    @Transactional
    public Usuario consumir(String tokenBruto) {
        PasswordResetToken token = repository.findByTokenHash(OpaqueTokenGenerator.hash(tokenBruto))
                .orElseThrow(() -> new UnauthorizedException("Token de redefinição de senha inválido."));
        if (token.isUsado()) {
            throw new UnauthorizedException("Token de redefinição de senha já utilizado.");
        }
        if (token.isExpirado(Instant.now())) {
            throw new UnauthorizedException("Token de redefinição de senha expirado.");
        }
        token.setUsedAt(Instant.now());
        repository.save(token);
        return token.getUsuario();
    }
}
