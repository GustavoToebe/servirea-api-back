package br.com.servire.api.billing.dto;

import br.com.servire.api.billing.Periodicidade;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NovoPrecoRequest(
        @NotNull Periodicidade periodicidade,
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal valor,
        @NotNull LocalDate vigenteDesde
) {
}
