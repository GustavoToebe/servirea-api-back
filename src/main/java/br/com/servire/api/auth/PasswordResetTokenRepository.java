package br.com.servire.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @org.springframework.data.jpa.repository.Query("select t.usuario.id from PasswordResetToken t where t.tokenHash=:hash")
    Optional<UUID> usuarioDoToken(String hash);
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
