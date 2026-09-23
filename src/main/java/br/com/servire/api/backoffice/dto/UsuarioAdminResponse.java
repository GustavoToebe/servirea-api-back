package br.com.servire.api.backoffice.dto;

import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioTenant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UsuarioAdminResponse(
        UUID id,
        String nome,
        String email,
        boolean ativo,
        Instant createdAt,
        List<VinculoParoquiaResponse> vinculos
) {

    public static UsuarioAdminResponse de(Usuario usuario, List<UsuarioTenant> vinculos) {
        return new UsuarioAdminResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.isAtivo(),
                usuario.getCreatedAt(),
                vinculos.stream().map(VinculoParoquiaResponse::de).toList());
    }
}
