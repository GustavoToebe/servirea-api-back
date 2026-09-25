package br.com.servire.api.tenant.dto;

import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corpo do {@code PUT /tenant}. {@code codigo}, {@code slug} e
 * {@code status} não entram (slug quebra URL pública; status é Kill Switch).
 * {@code diocese} é o nome, escolhido da lista ou digitado; vazio = sem
 * diocese (V037: só agrupamento informativo).
 */
public record TenantRequest(
        @NotBlank String nome,
        String razaoSocial,
        String cnpj,
        @Size(max = 150) String diocese,
        List<@Valid ContatoEmailRequest> emails,
        List<@Valid ContatoTelefoneRequest> telefones
) {
}
