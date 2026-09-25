package br.com.servire.api.tenant;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório do "Master lógico" (seção 25) — {@link Tenant} é tabela
 * global, sem filtro de isolamento.
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID>, JpaSpecificationExecutor<Tenant> {

    @Override
    @EntityGraph(attributePaths = {"emails", "telefones"})
    Optional<Tenant> findById(UUID id);

    Optional<Tenant> findBySlug(String slug);

    boolean existsByCodigo(String codigo);

    boolean existsBySlug(String slug);

    long countByStatus(Tenant.Status status);

    /** Soma o espelho {@code voluntarios_ativos} das paróquias da diocese. */
    @Query("SELECT COALESCE(SUM(t.voluntariosAtivos), 0) FROM Tenant t WHERE t.diocese.id = :dioceseId")
    long somarVoluntariosAtivos(@Param("dioceseId") UUID dioceseId);
}
