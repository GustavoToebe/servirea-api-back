package br.com.servire.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Chave primária composta de {@link UsuarioTenant} — espelha
 * {@code PRIMARY KEY (usuario_id, tenant_id)} da migration V018.
 *
 * <p>Precisa implementar {@link Serializable} e {@code equals}/
 * {@code hashCode} por valor: é um requisito da especificação JPA para
 * qualquer classe usada como {@code @EmbeddedId} (o Hibernate usa esses
 * métodos para comparar identidades de entidade em coleções/cache de
 * sessão).</p>
 */
@Embeddable
public class UsuarioTenantId implements Serializable {

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    protected UsuarioTenantId() {
        // JPA
    }

    public UsuarioTenantId(UUID usuarioId, UUID tenantId) {
        this.usuarioId = usuarioId;
        this.tenantId = tenantId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UsuarioTenantId that)) {
            return false;
        }
        return Objects.equals(usuarioId, that.usuarioId) && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(usuarioId, tenantId);
    }
}
