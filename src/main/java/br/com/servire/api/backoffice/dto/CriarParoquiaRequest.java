package br.com.servire.api.backoffice.dto;

import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Provisionamento de cliente (seção 71): tenant em {@code TRIAL} +
 * primeiro ADMIN.
 */
public record CriarParoquiaRequest(
        @NotBlank String codigo,
        @NotBlank String slug,
        @NotBlank String nome,
        String razaoSocial,
        String cnpj,
        List<@Valid ContatoEmailRequest> emails,
        List<@Valid ContatoTelefoneRequest> telefones,
        String cep,
        String cidade,
        String uf,
        String bairro,
        String logradouro,
        String numero,
        String complemento,
        String observacoes,
        @NotNull @Valid AdminInicial admin
) {

    public record AdminInicial(
            @NotBlank String nome,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String senha
    ) {
    }
}
