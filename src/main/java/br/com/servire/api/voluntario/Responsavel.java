package br.com.servire.api.voluntario;

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
 * Responsável legal de um voluntário (seção 38/106 do plano mestre) —
 * mapeia {@code responsaveis} (V004). Cada voluntário pode ter vários,
 * mas deve existir exatamente um com {@code principal=true}; essa regra
 * NÃO é reforçada aqui na entidade (o banco só garante, via
 * {@code uq_responsavel_principal_por_voluntario}, que não pode haver
 * MAIS de um principal — não que tenha pelo menos um) — quem garante
 * "exatamente um" é {@link VoluntarioService}, na mesma transação que
 * grava o voluntário (seção 39).
 *
 * <p>Sem {@code @TenantId} próprio: {@code responsaveis} não tem coluna
 * {@code tenant_id} (V004) — o isolamento entre tenants vem
 * transitivamente da FK {@code voluntario_id}, que aponta para uma linha
 * de {@code voluntarios} já filtrada por tenant. Por isso este
 * repositório NUNCA deve ser consultado direto por um {@code id} de
 * responsável vindo de fora sem antes validar que o {@code voluntario}
 * pai pertence ao tenant atual (ver {@link VoluntarioService}) — buscar
 * um {@code Responsavel} solto, sem passar pelo voluntário, não tem
 * proteção de tenant nenhuma.</p>
 */
@Entity
@Table(name = "responsaveis")
public class Responsavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voluntario_id", nullable = false)
    private Voluntario voluntario;

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

    protected Responsavel() {
        // JPA
    }

    public Responsavel(String parentesco, String nome, String telefone, String celular, String email, boolean principal) {
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

    public Voluntario getVoluntario() {
        return voluntario;
    }

    public void setVoluntario(Voluntario voluntario) {
        this.voluntario = voluntario;
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
}
