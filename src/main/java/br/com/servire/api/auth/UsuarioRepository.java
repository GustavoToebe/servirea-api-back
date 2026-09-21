package br.com.servire.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório do "Master lógico" (seção 25 do plano mestre) — {@link Usuario}
 * é uma tabela global, sem {@code tenant_id} (um mesmo usuário pode
 * pertencer a mais de uma paróquia, seção 29).
 */
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);
}
