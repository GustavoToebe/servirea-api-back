package br.com.servire.api.billing.dto;

import br.com.servire.api.billing.Periodicidade;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contratar/trocar plano. {@code valor} omitido = preço vigente do
 * catálogo; informado = valor negociado. {@code diaVencimento} vai até 28
 * para existir em todo mês.
 */
public record CriarAssinaturaRequest(
        @NotNull UUID planoId,
        @NotNull Periodicidade periodicidade,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal valor,
        @NotNull @Min(1) @Max(28) Integer diaVencimento,
        @NotNull LocalDate inicio,
        String observacoes
) {
}
