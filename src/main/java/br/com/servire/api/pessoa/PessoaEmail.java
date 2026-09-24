package br.com.servire.api.pessoa;

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
@Table(name = "pessoa_email")
public class PessoaEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private boolean principal;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected PessoaEmail() {
    }

    public PessoaEmail(String tipo, String email, boolean principal) {
        this.tipo = tipo;
        this.email = email;
        this.principal = principal;
    }

    public UUID getId() {
        return id;
    }

    public Pessoa getPessoa() {
        return pessoa;
    }

    public void setPessoa(Pessoa pessoa) {
        this.pessoa = pessoa;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
