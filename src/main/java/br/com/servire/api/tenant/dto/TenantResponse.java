package br.com.servire.api.tenant.dto;

import br.com.servire.api.tenant.Tenant;

import java.util.UUID;

public record TenantResponse(
        UUID id,
        String codigo,
        String slug,
        String nome,
        String razaoSocial,
        String cnpj,
        Tenant.Status status
) {

    public static TenantResponse de(Tenant t) {
        return new TenantResponse(
                t.getId(), t.getCodigo(), t.getSlug(), t.getNome(),
                t.getRazaoSocial(), t.getCnpj(), t.getStatus());
    }
}
