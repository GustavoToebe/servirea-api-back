package br.com.servire.api.tenant.dto;

import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Corpo do {@code PUT /tenant}. {@code codigo}, {@code slug} e
 * {@code status} não entram (slug quebra URL pública; status é Kill Switch).
 */
public record TenantRequest(
        @NotBlank String nome,
        String razaoSocial,
        String cnpj,
        List<@Valid ContatoEmailRequest> emails,
        List<@Valid ContatoTelefoneRequest> telefones
) {
}
