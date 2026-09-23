package br.com.servire.api.backoffice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizarUsuarioRequest(
        @NotBlank String nome,
        @NotBlank @Email String email,
        @Size(min = 8) String senha,
        @NotNull Boolean ativo
) {
}
