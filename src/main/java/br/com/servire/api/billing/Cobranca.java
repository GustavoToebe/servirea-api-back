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
 * Um período cobrado de uma assinatura (um mês no plano mensal, 12 meses
 * no anual). O pagamento é registrado à mão pelo operador (PIX manual,
 * 131.3) — forma, data e valor pago ficam na própria linha. Tabela global,
 * como {@link Assinatura}.
 *
 * <p>"Vencida" não é status gravado: é {@code ABERTA} com
 * {@code vencimento < hoje}. Assim nenhum job precisa "virar" o status, e
 * estornar um pagamento antigo faz a cobrança voltar a aparecer vencida.</p>
 */
@Entity
@Table(name = "cobranca")
public class Cobranca {

    public enum Status {
        ABERTA,
        PAGA,
        CANCELADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "assinatura_id", nullable = false, updatable = false)
    private UUID assinaturaId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "competencia_inicio", nullable = false)
    private LocalDate competenciaInicio;

    @Column(name = "competencia_fim", nullable = false)
    private LocalDate competenciaFim;

    @Column(nullable = false)
    private LocalDate vencimento;

    @Column(nullable = false)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.ABERTA;

    @Column(name = "pago_em")
    private LocalDate pagoEm;

    @Column(name = "valor_pago")
    private BigDecimal valorPago;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "forma_pagamento")
    private FormaPagamento formaPagamento;

    private String observacao;

    @Column(name = "registrado_por")
    private UUID registradoPor;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Cobranca() {
        // JPA
    }

    public Cobranca(Assinatura assinatura, LocalDate competenciaInicio, LocalDate competenciaFim,
                    LocalDate vencimento) {
        Objects.requireNonNull(assinatura, "assinatura não pode ser nula");
        this.assinaturaId = Objects.requireNonNull(assinatura.getId(), "assinatura precisa estar salva");
        this.tenantId = assinatura.getTenantId();
        this.valor = assinatura.getValor();
        this.competenciaInicio = Objects.requireNonNull(competenciaInicio, "competenciaInicio não pode ser nula");
        this.competenciaFim = Objects.requireNonNull(competenciaFim, "competenciaFim não pode ser nula");
        this.vencimento = Objects.requireNonNull(vencimento, "vencimento não pode ser nulo");
    }

    public void pagar(LocalDate pagoEm, BigDecimal valorPago, FormaPagamento forma, String observacao,
                      UUID registradoPor) {
        this.status = Status.PAGA;
        this.pagoEm = Objects.requireNonNull(pagoEm, "pagoEm não pode ser nulo");
        this.valorPago = Objects.requireNonNull(valorPago, "valorPago não pode ser nulo");
        this.formaPagamento = Objects.requireNonNull(forma, "forma não pode ser nula");
        this.observacao = observacao;
        this.registradoPor = registradoPor;
    }

    /** Desfaz um pagamento lançado por engano: a cobrança volta a ficar em aberto. */
    public void estornar() {
        this.status = Status.ABERTA;
        this.pagoEm = null;
        this.valorPago = null;
        this.formaPagamento = null;
        this.registradoPor = null;
    }

    /** Isenta/cancela (cortesia, troca de plano). O motivo fica em {@code observacao}. */
    public void cancelar(String motivo) {
        this.status = Status.CANCELADA;
        if (motivo != null) {
            this.observacao = motivo;
        }
    }

    public boolean vencidaEm(LocalDate hoje) {
        return status == Status.ABERTA && vencimento.isBefore(hoje);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAssinaturaId() {
        return assinaturaId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public LocalDate getCompetenciaInicio() {
        return competenciaInicio;
    }

    public LocalDate getCompetenciaFim() {
        return competenciaFim;
    }

    public LocalDate getVencimento() {
        return vencimento;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getPagoEm() {
        return pagoEm;
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public String getObservacao() {
        return observacao;
    }

    public UUID getRegistradoPor() {
        return registradoPor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
