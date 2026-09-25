package br.com.servire.api.tenant.dto;

import br.com.servire.api.pessoa.dto.ContatoEmailResponse;
import br.com.servire.api.pessoa.dto.ContatoTelefoneResponse;
import br.com.servire.api.tenant.Tenant;

import java.util.List;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String codigo,
        String slug,
        String nome,
        String razaoSocial,
        String cnpj,
        String diocese,
        Tenant.Status status,
        List<ContatoEmailResponse> emails,
        List<ContatoTelefoneResponse> telefones
) {

    public static TenantResponse de(Tenant t) {
        return new TenantResponse(
                t.getId(), t.getCodigo(), t.getSlug(), t.getNome(),
                t.getRazaoSocial(), t.getCnpj(),
                t.getDiocese() == null ? null : t.getDiocese().getNome(),
                t.getStatus(),
                t.getEmails().stream()
                        .map(e -> new ContatoEmailResponse(e.getId(), e.getTipo(), e.getEmail(), e.isPrincipal()))
                        .toList(),
                t.getTelefones().stream()
                        .map(tel -> new ContatoTelefoneResponse(tel.getId(), tel.getTipo(), tel.getNumero(), tel.isPrincipal()))
                        .toList());
    }
}
