package br.com.servire.api.inscricao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inscricao_telefone")
public class InscricaoTelefone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inscricao_id", nullable = false)
    private Inscricao inscricao;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private String numero;

    @Column(nullable = false)
    private boolean principal;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected InscricaoTelefone() {
    }

    public InscricaoTelefone(String tipo, String numero, boolean principal) {
        this.tipo = tipo;
        this.numero = numero;
        this.principal = principal;
    }

    public UUID getId() {
        return id;
    }

    public Inscricao getInscricao() {
        return inscricao;
    }

    public void setInscricao(Inscricao inscricao) {
        this.inscricao = inscricao;
    }

    public String getTipo() {
        return tipo;
    }

    public String getNumero() {
        return numero;
    }

    public boolean isPrincipal() {
        return principal;
    }
}
