package br.com.servire.api.auth.dto;

public record AccessTokenResponse(String accessToken, long expiresInSeconds, TenantResumo tenantAtual) {
}
