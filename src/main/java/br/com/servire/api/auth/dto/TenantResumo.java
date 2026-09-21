package br.com.servire.api.auth.dto;

import br.com.servire.api.tenant.Tenant;

import java.util.UUID;

/**
 * Representação mínima de um tenant para as respostas de login/seleção
 * (seção 30) — nunca expor o {@link Tenant} completo (não vaza cnpj/razão
 * social num payload de autenticação).
 */
public record TenantResumo(UUID id, String nome, String slug) {

    public static TenantResumo de(Tenant tenant) {
        return new TenantResumo(tenant.getId(), tenant.getNome(), tenant.getSlug());
    }
}
