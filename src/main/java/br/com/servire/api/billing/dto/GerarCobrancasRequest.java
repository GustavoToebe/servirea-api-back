package br.com.servire.api.billing.dto;

import jakarta.validation.constraints.NotNull;

import java.time.YearMonth;

/** Gera cobranças adiantadas até o mês {@code ate} (JSON {@code "2027-03"}), para receber meses à frente. */
public record GerarCobrancasRequest(@NotNull YearMonth ate) {
}
