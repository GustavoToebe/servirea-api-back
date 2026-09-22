package br.com.servire.api.tenant.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo do {@code PUT /tenant}. Só os campos editáveis da paróquia —
 * {@code codigo}, {@code slug} e {@code status} não entram (slug quebra
 * URL pública; status é Kill Switch, seção 28).
 */
public record TenantRequest(
        @NotBlank String nome,
        String razaoSocial,
        String cnpj
) {
}
