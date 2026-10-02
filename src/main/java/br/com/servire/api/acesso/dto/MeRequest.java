package br.com.servire.api.acesso.dto;

import jakarta.validation.constraints.NotBlank;

public record MeRequest(
        @NotBlank String nome,
        String tipoTelefone,
        String telefone,
        String senha,
        String senhaAtual,
        @jakarta.validation.constraints.Size(max=64) String codigoMfa
) {
}
