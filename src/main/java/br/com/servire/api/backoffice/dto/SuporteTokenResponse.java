package br.com.servire.api.backoffice.dto;

import br.com.servire.api.auth.dto.TenantResumo;

public record SuporteTokenResponse(
        String accessToken,
        long expiresInSeconds,
        TenantResumo tenantAtual,
        boolean suporte
) {
}
