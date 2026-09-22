package br.com.servire.api.auth;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioTenantRepository extends JpaRepository<UsuarioTenant, UsuarioTenantId> {

    List<UsuarioTenant> findByUsuario_IdAndStatus(UUID usuarioId, UsuarioTenant.Status status);

    /**
     * Usado pelo {@code JwtAuthenticationFilter} no Kill Switch, fora de
     * qualquer {@code @Transactional}: com {@code open-in-view: false},
     * {@code UsuarioTenant.tenant} é LAZY e a sessão fecha ao sair deste
     * método — sem o {@code EntityGraph}, {@code getTenant().getStatus()}
     * no filtro estoura {@code LazyInitializationException}, o Boot
     * despacha para {@code /error} e o cliente vê um 401 genérico
     * ({@code path=/error}, {@code requestId=null}) em vez do erro real.
     */
    @EntityGraph(attributePaths = "tenant")
    Optional<UsuarioTenant> findByUsuario_IdAndTenant_Id(UUID usuarioId, UUID tenantId);
}
