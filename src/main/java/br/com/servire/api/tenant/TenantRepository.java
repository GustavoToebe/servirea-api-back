package br.com.servire.api.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório do "Master lógico" (seção 25 do plano mestre) — {@link Tenant}
 * é uma tabela global, sem {@code tenant_id} e sem filtro de isolamento:
 * qualquer linha pode ser lida por qualquer código da aplicação (é a
 * própria raiz da hierarquia multi-tenant, seção 17/27).
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);
}
