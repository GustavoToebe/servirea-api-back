package br.com.servire.api.integracao.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ProvisionarInstanciaRequest(
        @NotNull UUID contratacaoId,
        UUID clienteId,
        @NotNull @Valid Instancia instancia,
        @NotNull @Valid Administrador administrador,
        @NotNull @Valid DireitosInstancia direitos
) {
    public record Instancia(@NotBlank String nome, @NotBlank String slug) {
    }

    public record Administrador(@NotBlank String nome, @NotBlank @Email String email) {
    }
}
