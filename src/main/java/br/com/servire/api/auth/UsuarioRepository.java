package br.com.servire.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório do "Master lógico" (seção 25 do plano mestre) — {@link Usuario}
 * é uma tabela global, sem {@code tenant_id} (um mesmo usuário pode
 * pertencer a mais de uma paróquia, seção 29).
 */
public interface UsuarioRepository extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
