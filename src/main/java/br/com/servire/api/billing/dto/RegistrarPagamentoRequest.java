package br.com.servire.api.billing.dto;

import br.com.servire.api.billing.FormaPagamento;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Registro manual de pagamento (PIX manual, 131.3) de uma ou mais
 * cobranças — pagar vários meses de uma vez. {@code valorPago} só é aceito
 * com uma cobrança; omitido, vale o valor de cada cobrança.
 */
public record RegistrarPagamentoRequest(
        @NotEmpty List<@NotNull UUID> cobrancaIds,
        @NotNull LocalDate pagoEm,
        @NotNull FormaPagamento formaPagamento,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal valorPago,
        String observacao
) {
}
