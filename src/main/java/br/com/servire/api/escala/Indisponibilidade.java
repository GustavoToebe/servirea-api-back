package br.com.servire.api.escala;

import br.com.servire.api.voluntario.Periodo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Data em que o voluntário NÃO pode servir (V045), digitada pela equipe para montar a mensal.
 * {@code periodo} nulo = o dia inteiro. Diferente de {@code DisponibilidadeVoluntario} (V025, positiva).
 */
@Entity
@Table(name = "indisponibilidade_voluntario")
public class Indisponibilidade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "voluntario_id", nullable = false)
    private UUID voluntarioId;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Periodo periodo;

    @Column(length = 200)
    private String observacao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Indisponibilidade() {
    }

    public Indisponibilidade(UUID voluntarioId, LocalDate data, Periodo periodo, String observacao) {
        this.voluntarioId = voluntarioId;
        this.data = data;
        this.periodo = periodo;
        this.observacao = observacao;
    }

    public UUID getId() { return id; }
    public UUID getVoluntarioId() { return voluntarioId; }
    public LocalDate getData() { return data; }
    public Periodo getPeriodo() { return periodo; }
    public String getObservacao() { return observacao; }
}
