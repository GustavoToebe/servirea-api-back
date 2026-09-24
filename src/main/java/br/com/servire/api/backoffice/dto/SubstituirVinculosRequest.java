package br.com.servire.api.backoffice.dto;

import br.com.servire.api.auth.UsuarioTenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SubstituirVinculosRequest(@NotNull List<@Valid Vinculo> vinculos) {

    public record Vinculo(
            @NotNull UUID tenantId,
            @NotNull UsuarioTenant.Role role,
            UsuarioTenant.Status status
    ) {
    }
}
