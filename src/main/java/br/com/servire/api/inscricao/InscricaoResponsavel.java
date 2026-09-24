package br.com.servire.api.inscricao;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Responsável informado no formulário público. Na aprovação vira
 * {@code pessoa} com papel RESPONSAVEL (reuso por e-mail principal)
 * e uma linha em {@code pessoa_relacao}.
 */
@Entity
@Table(name = "inscricao_responsaveis")
public class InscricaoResponsavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inscricao_id", nullable = false)
    private Inscricao inscricao;

    @Column(nullable = false)
    private String parentesco;

    @Column(name = "parentesco_inverso")
    private String parentescoInverso;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private boolean principal = false;

    @OneToMany(mappedBy = "responsavel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("principal DESC, email ASC")
    private List<InscricaoResponsavelEmail> emails = new ArrayList<>();

    @OneToMany(mappedBy = "responsavel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("principal DESC, numero ASC")
    private List<InscricaoResponsavelTelefone> telefones = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected InscricaoResponsavel() {
    }

    public InscricaoResponsavel(String parentesco, String nome, boolean principal) {
        this.parentesco = parentesco;
        this.nome = nome;
        this.principal = principal;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Inscricao getInscricao() {
        return inscricao;
    }

    public void setInscricao(Inscricao inscricao) {
        this.inscricao = inscricao;
    }

    public String getParentesco() {
        return parentesco;
    }

    public String getParentescoInverso() {
        return parentescoInverso;
    }

    public void setParentescoInverso(String parentescoInverso) {
        this.parentescoInverso = parentescoInverso;
    }

    public String getNome() {
        return nome;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public List<InscricaoResponsavelEmail> getEmails() {
        return emails;
    }

    public List<InscricaoResponsavelTelefone> getTelefones() {
        return telefones;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
