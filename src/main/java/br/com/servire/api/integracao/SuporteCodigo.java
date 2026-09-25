package br.com.servire.api.integracao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suporte_codigo")
public class SuporteCodigo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "hash_codigo", nullable = false, unique = true)
    private String hashCodigo;

    @Column(name = "operador_nome", nullable = false)
    private String operadorNome;

    @Column(name = "operador_email", nullable = false)
    private String operadorEmail;

    @Column(nullable = false)
    private String motivo;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    protected SuporteCodigo() {
    }

    public SuporteCodigo(UUID tenantId, String hashCodigo, String operadorNome, String operadorEmail,
                         String motivo, Instant expiraEm) {
        this.tenantId = tenantId;
        this.hashCodigo = hashCodigo;
        this.operadorNome = operadorNome;
        this.operadorEmail = operadorEmail;
        this.motivo = motivo;
        this.expiraEm = expiraEm;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getOperadorNome() {
        return operadorNome;
    }

    public String getOperadorEmail() {
        return operadorEmail;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getUsadoEm() {
        return usadoEm;
    }

    public void setUsadoEm(Instant usadoEm) {
        this.usadoEm = usadoEm;
    }
}
