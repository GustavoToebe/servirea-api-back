package br.com.servire.api.backoffice.dto;

import br.com.servire.api.auth.UsuarioTenant;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CriarUsuarioRequest(
        @NotBlank String nome,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String senha,
        Boolean ativo,
        List<Vinculo> vinculos
) {

    public record Vinculo(
            @NotNull UUID tenantId,
            @NotNull UsuarioTenant.Role role,
            UsuarioTenant.Status status
    ) {
    }
}
