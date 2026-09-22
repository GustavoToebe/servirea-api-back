package br.com.servire.api.inscricao.dto;

import br.com.servire.api.inscricao.InscricaoResponsavel;

import java.util.UUID;

public record InscricaoResponsavelResponse(
        UUID id, String parentesco, String nome, String telefone, String celular, String email, boolean principal) {

    public static InscricaoResponsavelResponse de(InscricaoResponsavel r) {
        return new InscricaoResponsavelResponse(
                r.getId(), r.getParentesco(), r.getNome(), r.getTelefone(), r.getCelular(), r.getEmail(), r.isPrincipal());
    }
}
