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

/**
 * Relação responsável ↔ voluntário. {@code parentesco} é o "é" do
 * responsável (Pai, Mãe); {@code parentescoInverso} é o rótulo do
 * voluntário (Filho) — os dois lados da tela "é / de".
 */
@Entity
@Table(name = "pessoa_relacao")
public class PessoaRelacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false)
    private Pessoa responsavel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voluntario_id", nullable = false)
    private Pessoa voluntario;

    @Column(nullable = false)
    private String parentesco;

    @Column(name = "parentesco_inverso")
    private String parentescoInverso;

    @Column(nullable = false)
    private boolean principal;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected PessoaRelacao() {
    }

    public PessoaRelacao(Pessoa responsavel, Pessoa voluntario, String parentesco, String parentescoInverso,
                         boolean principal) {
        this.responsavel = responsavel;
        this.voluntario = voluntario;
        this.parentesco = parentesco;
        this.parentescoInverso = parentescoInverso;
        this.principal = principal;
    }

    public UUID getId() {
        return id;
    }

    public Pessoa getResponsavel() {
        return responsavel;
    }

    public Pessoa getVoluntario() {
        return voluntario;
    }

    public String getParentesco() {
        return parentesco;
    }

    public String getParentescoInverso() {
        return parentescoInverso;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
