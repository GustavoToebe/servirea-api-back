package br.com.servire.api.backoffice.dto;

public record BackofficeLoginResponse(String accessToken, long expiresInSeconds) {
}
