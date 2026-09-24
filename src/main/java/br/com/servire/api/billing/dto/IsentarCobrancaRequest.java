package br.com.servire.api.billing.dto;

import jakarta.validation.constraints.Size;

/** Motivo da isenção/cancelamento da cobrança (cortesia, negociação...). Opcional. */
public record IsentarCobrancaRequest(@Size(max = 500) String motivo) {
}
