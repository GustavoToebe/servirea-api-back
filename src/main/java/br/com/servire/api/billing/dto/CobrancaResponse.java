package br.com.servire.api.billing.dto;

import br.com.servire.api.billing.Cobranca;
import br.com.servire.api.billing.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** {@code vencida} é calculado (ABERTA com vencimento passado), não gravado. */
public record CobrancaResponse(
        UUID id,
        UUID assinaturaId,
        LocalDate competenciaInicio,
        LocalDate competenciaFim,
        LocalDate vencimento,
        BigDecimal valor,
        Cobranca.Status status,
        boolean vencida,
        LocalDate pagoEm,
        BigDecimal valorPago,
        FormaPagamento formaPagamento,
        String observacao
) {

    public static CobrancaResponse de(Cobranca c, LocalDate hoje) {
        return new CobrancaResponse(c.getId(), c.getAssinaturaId(), c.getCompetenciaInicio(),
                c.getCompetenciaFim(), c.getVencimento(), c.getValor(), c.getStatus(), c.vencidaEm(hoje),
                c.getPagoEm(), c.getValorPago(), c.getFormaPagamento(), c.getObservacao());
    }
}
