package br.com.servire.api.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Catálogo de planos do SaaS (seção 63). Tabela global — sem
 * {@code @TenantId}. O preço NÃO fica aqui: vive em {@link PrecoPlano},
 * com histórico, para um reajuste não alterar contrato antigo.
 */
@Entity
@Table(name = "plano")
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    @Column(name = "limite_voluntarios")
    private Integer limiteVoluntarios;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Plano() {
        // JPA
    }

    public Plano(String codigo, String nome) {
        this.codigo = Objects.requireNonNull(codigo, "codigo não pode ser nulo");
        this.nome = Objects.requireNonNull(nome, "nome não pode ser nulo");
    }

    public UUID getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Integer getLimiteVoluntarios() {
        return limiteVoluntarios;
    }

    public void setLimiteVoluntarios(Integer limiteVoluntarios) {
        this.limiteVoluntarios = limiteVoluntarios;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
