package br.com.servire.api.backoffice.dto;

import br.com.servire.api.auth.UsuarioTenant;

import java.util.UUID;

public record VinculoParoquiaResponse(
        UUID tenantId,
        String tenantNome,
        String tenantSlug,
        UsuarioTenant.Role role,
        UsuarioTenant.Status status
) {

    public static VinculoParoquiaResponse de(UsuarioTenant vinculo) {
        return new VinculoParoquiaResponse(
                vinculo.getTenant().getId(),
                vinculo.getTenant().getNome(),
                vinculo.getTenant().getSlug(),
                vinculo.getRole(),
                vinculo.getStatus());
    }
}
