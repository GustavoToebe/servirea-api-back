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

/**
 * Responsável informado no formulário público de inscrição (Fase 8, seção
 * 108 do plano mestre) — mapeia {@code inscricao_responsaveis} (V009 +
 * {@code tenant_id} de V021). Mesmo formato de {@link
 * br.com.servire.api.voluntario.Responsavel}, mas para uma
 * {@link Inscricao} em vez de um {@code Voluntario} — os dois viram linhas
 * de {@code responsaveis} de verdade só quando a inscrição é aprovada (ver
 * {@link InscricaoService#aprovar}).
 *
 * <p>Diferente de {@code Responsavel} (V004, sem {@code updated_at}),
 * {@code inscricao_responsaveis} TEM coluna {@code updated_at} com
 * trigger (V009) — mapeada aqui como {@code insertable=false,
 * updatable=false}, gerida só pelo banco.</p>
 *
 * <p>Já nasce com {@code @TenantId} correto (diferente de
 * {@code Responsavel}, cujo bug de {@code tenant_id} ausente só foi
 * corrigido depois de já commitado — ver javadoc de {@code Responsavel})
 * porque esta classe foi escrita depois de reler {@code V021} por
 * completo.</p>
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

    @Column(nullable = false)
    private String nome;

    private String telefone;

    private String celular;

    private String email;

    @Column(nullable = false)
    private boolean principal = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected InscricaoResponsavel() {
        // JPA
    }

    public InscricaoResponsavel(String parentesco, String nome, String telefone, String celular, String email, boolean principal) {
        this.parentesco = parentesco;
        this.nome = nome;
        this.telefone = telefone;
        this.celular = celular;
        this.email = email;
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

    public void setParentesco(String parentesco) {
        this.parentesco = parentesco;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public void setPrincipal(boolean principal) {
        this.principal = principal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
