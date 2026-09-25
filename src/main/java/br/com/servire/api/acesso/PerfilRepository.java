package br.com.servire.api.acesso;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PerfilRepository extends JpaRepository<Perfil, UUID> {

    @EntityGraph(attributePaths = "permissoes")
    List<Perfil> findByTenantIdOrderByNomeAsc(UUID tenantId);

    @EntityGraph(attributePaths = "permissoes")
    Optional<Perfil> findByIdAndTenantId(UUID id, UUID tenantId);

    @EntityGraph(attributePaths = "permissoes")
    Optional<Perfil> findByTenantIdAndNome(UUID tenantId, String nome);

    long countByTenantIdAndAcessoTotalTrueAndAtivoTrue(UUID tenantId);
}
