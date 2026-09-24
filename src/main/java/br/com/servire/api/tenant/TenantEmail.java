package br.com.servire.api.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * E-mail da paróquia. Tabela global (sem {@code @TenantId}) — o
 * {@code tenant_id} é FK comum, igual a {@code assinatura}/{@code cobranca}.
 */
@Entity
@Table(name = "tenant_email")
public class TenantEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private boolean principal;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected TenantEmail() {
    }

    public TenantEmail(String tipo, String email, boolean principal) {
        this.tipo = tipo;
        this.email = email;
        this.principal = principal;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public String getTipo() {
        return tipo;
    }

    public String getEmail() {
        return email;
    }

    public boolean isPrincipal() {
        return principal;
    }
}
