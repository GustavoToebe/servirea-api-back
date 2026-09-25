package br.com.servire.api.diocese;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Diocese que agrupa paróquias (V034). Tabela global, sem
 * {@code @TenantId}. {@code cotaVoluntarios} nulo significa sem teto.
 */
@Entity
@Table(name = "diocese")
public class Diocese {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    private String uf;

    @Column(name = "cota_voluntarios")
    private Integer cotaVoluntarios;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Diocese() {
    }

    public Diocese(String nome) {
        this.nome = nome;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf;
    }

    public Integer getCotaVoluntarios() {
        return cotaVoluntarios;
    }

    public void setCotaVoluntarios(Integer cotaVoluntarios) {
        this.cotaVoluntarios = cotaVoluntarios;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
