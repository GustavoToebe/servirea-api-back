package br.com.servire.api.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Contrato de uma paróquia com um plano (seção 63). Tabela global —
 * {@code tenantId} é FK comum, NÃO {@code @TenantId}: o operador lê e
 * escreve sem {@code TenantContext}. {@code valor} é copiado do preço
 * vigente na contratação (e pode ser negociado), para um reajuste do
 * catálogo nunca mudar o que a paróquia já contratou.
 *
 * <p>{@code plano} é EAGER de propósito: toda resposta mostra o nome do
 * plano e {@code open-in-view} está desligado.</p>
 */
@Entity
@Table(name = "assinatura")
public class Assinatura {

    public enum Status {
        ATIVA,
        CANCELADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plano_id", nullable = false)
    private Plano plano;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Periodicidade periodicidade;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(name = "dia_vencimento", nullable = false)
    private int diaVencimento;

    @Column(nullable = false)
    private LocalDate inicio;

    private LocalDate fim;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.ATIVA;

    private String observacoes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Assinatura() {
        // JPA
    }

    public Assinatura(UUID tenantId, Plano plano, Periodicidade periodicidade, BigDecimal valor,
                      int diaVencimento, LocalDate inicio) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId não pode ser nulo");
        this.plano = Objects.requireNonNull(plano, "plano não pode ser nulo");
        this.periodicidade = Objects.requireNonNull(periodicidade, "periodicidade não pode ser nula");
        this.valor = Objects.requireNonNull(valor, "valor não pode ser nulo");
        this.diaVencimento = diaVencimento;
        this.inicio = Objects.requireNonNull(inicio, "inicio não pode ser nulo");
    }

    /** Encerra o contrato; as cobranças abertas futuras são canceladas pelo serviço. */
    public void encerrar(LocalDate fim) {
        this.status = Status.CANCELADA;
        this.fim = fim.isBefore(inicio) ? inicio : fim;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Plano getPlano() {
        return plano;
    }

    public Periodicidade getPeriodicidade() {
        return periodicidade;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public int getDiaVencimento() {
        return diaVencimento;
    }

    public LocalDate getInicio() {
        return inicio;
    }

    public LocalDate getFim() {
        return fim;
    }

    public Status getStatus() {
        return status;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
