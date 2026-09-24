package br.com.servire.api.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssinaturaRepository extends JpaRepository<Assinatura, UUID> {

    Optional<Assinatura> findByTenantIdAndStatus(UUID tenantId, Assinatura.Status status);

    List<Assinatura> findByTenantIdOrderByInicioDescCreatedAtDesc(UUID tenantId);

    List<Assinatura> findByStatus(Assinatura.Status status);

    List<Assinatura> findByTenantIdInAndStatus(List<UUID> tenantIds, Assinatura.Status status);
}
