package br.com.servire.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Usado na detecção de reuso de token (seção 36): quando um refresh
     * token já revogado é apresentado de novo, é tratado como possível
     * roubo de token — revoga TODOS os refresh tokens ativos daquele
     * usuário de uma vez, forçando login de novo em todos os dispositivos.
     *
     * <p><b>Bug real #9 (seção 105):</b> {@code RefreshTokenService.rotacionar}
     * chama este método e, na sequência, lança {@code UnauthorizedException}
     * (unchecked) dentro da MESMA transação física — pela regra padrão do
     * Spring, qualquer {@code RuntimeException} que escape de um método
     * {@code @Transactional} marca a transação como rollback-only, e como
     * os dois participam da mesma transação (propagação padrão
     * {@code REQUIRED}), o rollback desfazia silenciosamente a revogação
     * que a detecção de reuso deveria garantir. {@code REQUIRES_NEW} força
     * este UPDATE a rodar e commitar numa transação própria, imune ao
     * rollback que a exceção lançada depois provoca na transação do
     * chamador — a funcionalidade de segurança nunca funcionou de fato.</p>
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :agora "
            + "WHERE rt.usuario.id = :usuarioId AND rt.revokedAt IS NULL")
    int revogarTodosAtivosDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("agora") Instant agora);
}
