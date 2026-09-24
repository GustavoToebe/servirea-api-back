package br.com.servire.api.inscricao.dto;

import br.com.servire.api.inscricao.InscricaoResponsavel;
import br.com.servire.api.pessoa.dto.ContatoEmailResponse;
import br.com.servire.api.pessoa.dto.ContatoTelefoneResponse;

import java.util.List;
import java.util.UUID;

public record InscricaoResponsavelResponse(
        UUID id,
        String parentesco,
        String parentescoInverso,
        String nome,
        List<ContatoEmailResponse> emails,
        List<ContatoTelefoneResponse> telefones,
        boolean principal) {

    public static InscricaoResponsavelResponse de(InscricaoResponsavel r) {
        return new InscricaoResponsavelResponse(
                r.getId(),
                r.getParentesco(),
                r.getParentescoInverso(),
                r.getNome(),
                r.getEmails().stream()
                        .map(e -> new ContatoEmailResponse(e.getId(), e.getTipo(), e.getEmail(), e.isPrincipal()))
                        .toList(),
                r.getTelefones().stream()
                        .map(t -> new ContatoTelefoneResponse(t.getId(), t.getTipo(), t.getNumero(), t.isPrincipal()))
                        .toList(),
                r.isPrincipal());
    }
}
