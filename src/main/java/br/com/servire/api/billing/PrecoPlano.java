package br.com.servire.api.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Preço de um plano a partir de uma data (seção 63). Nunca editado:
 * reajuste é uma linha nova com {@code vigenteDesde} maior. Vigente =
 * maior {@code vigente_desde <= hoje}.
 */
@Entity
@Table(name = "preco_plano")
public class PrecoPlano {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plano_id", nullable = false)
    private UUID planoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Periodicidade periodicidade;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected PrecoPlano() {
        // JPA
    }

    public PrecoPlano(UUID planoId, Periodicidade periodicidade, BigDecimal valor, LocalDate vigenteDesde) {
        this.planoId = Objects.requireNonNull(planoId, "planoId não pode ser nulo");
        this.periodicidade = Objects.requireNonNull(periodicidade, "periodicidade não pode ser nula");
        this.valor = Objects.requireNonNull(valor, "valor não pode ser nulo");
        this.vigenteDesde = Objects.requireNonNull(vigenteDesde, "vigenteDesde não pode ser nulo");
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanoId() {
        return planoId;
    }

    public Periodicidade getPeriodicidade() {
        return periodicidade;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
