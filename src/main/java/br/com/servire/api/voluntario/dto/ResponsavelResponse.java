package br.com.servire.api.voluntario.dto;

import br.com.servire.api.voluntario.Responsavel;

import java.util.UUID;

public record ResponsavelResponse(
        UUID id,
        String parentesco,
        String nome,
        String telefone,
        String celular,
        String email,
        boolean principal) {

    public static ResponsavelResponse de(Responsavel responsavel) {
        return new ResponsavelResponse(
                responsavel.getId(),
                responsavel.getParentesco(),
                responsavel.getNome(),
                responsavel.getTelefone(),
                responsavel.getCelular(),
                responsavel.getEmail(),
                responsavel.isPrincipal());
    }
}
