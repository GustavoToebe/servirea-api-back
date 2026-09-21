package br.com.servire.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SelectTenantRequest(@NotBlank String tokenSelecaoTenant, @NotNull UUID tenantId) {
}
