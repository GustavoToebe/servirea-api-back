package br.com.servire.api.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Criar/editar plano no catálogo. {@code codigo} só vale na criação
 * (é a chave estável, ex.: {@code STANDARD}); na edição é ignorado.
 */
public record PlanoRequest(
        String codigo,
        @NotBlank String nome,
        @Positive Integer limiteVoluntarios,
        Boolean ativo
) {
}
