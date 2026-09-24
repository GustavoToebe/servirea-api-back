package br.com.servire.api.backoffice.dto;

import java.math.BigDecimal;

/**
 * Cards do painel. {@code emAtraso} = paróquias com cobrança vencida em
 * aberto; {@code recebidoNoMes} = soma dos pagamentos registrados no mês
 * corrente (V029).
 */
public record DashboardResponse(long total, long ativas, long trial, long bloqueadas, long canceladas,
                                long emAtraso, BigDecimal recebidoNoMes) {
}
