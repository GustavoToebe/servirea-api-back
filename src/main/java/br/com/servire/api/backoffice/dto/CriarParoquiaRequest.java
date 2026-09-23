package br.com.servire.api.backoffice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Provisionamento de cliente (seção 71): tenant em {@code TRIAL} +
 * primeiro ADMIN (cria o {@code usuario} ou reaproveita se o e-mail já
 * existir — usuário é global).
 */
public record CriarParoquiaRequest(
        @NotBlank String codigo,
        @NotBlank String slug,
        @NotBlank String nome,
        String razaoSocial,
        String cnpj,
        String email,
        String telefone,
        String cep,
        String cidade,
        String uf,
        String bairro,
        String logradouro,
        String numero,
        String complemento,
        String observacoes,
        String tipoEmail,
        @NotNull @Valid AdminInicial admin
) {

    public record AdminInicial(
            @NotBlank String nome,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String senha
    ) {
    }
}
