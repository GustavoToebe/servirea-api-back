package br.com.servire.api.tenant;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório do "Master lógico" (seção 25) — {@link Tenant} é tabela
 * global, sem filtro de isolamento.
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID>, JpaSpecificationExecutor<Tenant> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Tenant t where t.id = :id")
    Optional<Tenant> bloquearParaCotas(UUID id);

    @org.springframework.data.jpa.repository.Query("select t from Tenant t order by t.id")
    java.util.List<Tenant> loteDaFila(org.springframework.data.domain.Pageable pagina);

    @Override
    @EntityGraph(attributePaths = {"emails", "telefones"})
    Optional<Tenant> findById(UUID id);

    Optional<Tenant> findBySlug(String slug);

    boolean existsByCodigo(String codigo);

    boolean existsBySlug(String slug);

    long countByStatus(Tenant.Status status);
}
