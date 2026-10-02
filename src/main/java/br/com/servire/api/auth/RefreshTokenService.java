package br.com.servire.api.auth;

import br.com.servire.api.security.OpaqueTokenGenerator;
import br.com.servire.api.security.SecurityProperties;
import br.com.servire.api.web.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh tokens com rotation (seção 35/36 do plano mestre). O algoritmo
 * de geração/hash do token está em {@link OpaqueTokenGenerator}
 * (compartilhado com {@link PasswordResetTokenService} — ver a javadoc de
 * lá para o porquê de SHA-256 em vez de bcrypt/argon2 aqui).
 *
 * <p>Rotation (seção 36): a cada uso, o token apresentado é revogado e um
 * novo é emitido, ligado ao anterior via {@code replaced_by}. Reuso de um
 * token JÁ revogado é tratado como evento de segurança — indício de que o
 * token foi copiado/roubado — e revoga de uma vez TODOS os refresh tokens
 * ativos daquele usuário, forçando login de novo em todos os
 * dispositivos.</p>
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityProperties properties;
    private final UsuarioRepository usuarios;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, SecurityProperties properties, UsuarioRepository usuarios) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
        this.usuarios = usuarios;
    }

    @Transactional
    public String emitir(Usuario usuario, String ip, String userAgent) {
        String tokenBruto = OpaqueTokenGenerator.gerar();
        RefreshToken entidade = new RefreshToken(
                usuario, OpaqueTokenGenerator.hash(tokenBruto),
                Instant.now().plus(properties.refreshTokenTtl()), ip, userAgent);
        refreshTokenRepository.save(entidade);
        return tokenBruto;
    }

    /**
     * @throws UnauthorizedException se o token for desconhecido, expirado,
     * ou já tiver sido usado (reuso detectado — todas as sessões do
     * usuário são revogadas antes de lançar a exceção).
     */
    @Transactional
    public RotacaoResultado rotacionar(String tokenBruto, String ip, String userAgent) {
        String hash = OpaqueTokenGenerator.hash(tokenBruto);
        UUID usuarioId = refreshTokenRepository.usuarioDoToken(hash).orElseThrow(() -> new UnauthorizedException("Refresh token desconhecido."));
        usuarios.buscarParaAlterar(usuarioId).orElseThrow(() -> new UnauthorizedException("Usuário não encontrado."));
        RefreshToken atual = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Refresh token desconhecido."));

        if (atual.isRevogado()) {
            refreshTokenRepository.revogarTodosAtivosDoUsuario(atual.getUsuario().getId(), Instant.now());
            throw new UnauthorizedException(
                    "Refresh token já utilizado. Por segurança, todas as sessões deste usuário foram encerradas.");
        }
        if (atual.isExpirado(Instant.now())) {
            throw new UnauthorizedException("Refresh token expirado.");
        }

        Usuario usuario = atual.getUsuario();
        String novoTokenBruto = OpaqueTokenGenerator.gerar();
        RefreshToken novo = new RefreshToken(
                usuario, OpaqueTokenGenerator.hash(novoTokenBruto),
                Instant.now().plus(properties.refreshTokenTtl()), ip, userAgent);
        refreshTokenRepository.save(novo);

        atual.setRevokedAt(Instant.now());
        atual.setReplacedBy(novo.getId());
        refreshTokenRepository.save(atual);

        return new RotacaoResultado(usuario, novoTokenBruto);
    }

    /**
     * Revoga TODOS os refresh tokens ativos do usuário — usado quando
     * trocar a senha (seção 32: trocar senha é um evento de segurança,
     * mesma reação usada para reuso de token detectado em
     * {@link #rotacionar}).
     */
    @Transactional
    public void revogarTodosDoUsuario(UUID usuarioId) {
        refreshTokenRepository.revogarTodosAtivosDoUsuario(usuarioId, Instant.now());
    }

    /**
     * Revoga um refresh token específico (logout de um dispositivo).
     * Idempotente e silencioso — não revela se o token existia, já estava
     * revogado ou nunca existiu (mesmo espírito de não vazar informação
     * das seções 21/79 aplicado aqui a tokens em vez de tenants).
     */
    @Transactional
    public void revogar(String tokenBruto) {
        refreshTokenRepository.findByTokenHash(OpaqueTokenGenerator.hash(tokenBruto)).ifPresent(rt -> {
            if (!rt.isRevogado()) {
                rt.setRevokedAt(Instant.now());
                refreshTokenRepository.save(rt);
            }
        });
    }

    @Transactional
    public void revogarNaTransacao(UUID id) {refreshTokenRepository.revogarNaTransacao(id,Instant.now());}

    public record RotacaoResultado(Usuario usuario, String novoTokenBruto) {
    }
}
