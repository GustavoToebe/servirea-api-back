package br.com.servire.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioTenantRepository extends JpaRepository<UsuarioTenant, UsuarioTenantId> {

    List<UsuarioTenant> findByUsuario_IdAndStatus(UUID usuarioId, UsuarioTenant.Status status);

    Optional<UsuarioTenant> findByUsuario_IdAndTenant_Id(UUID usuarioId, UUID tenantId);
}
