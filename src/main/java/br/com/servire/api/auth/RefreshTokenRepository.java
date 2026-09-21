package br.com.servire.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :agora "
            + "WHERE rt.usuario.id = :usuarioId AND rt.revokedAt IS NULL")
    int revogarTodosAtivosDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("agora") Instant agora);
}
