package br.com.servire.api.acesso.dto;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String nome,
        String email,
        String tipoTelefone,
        String telefone,
        String perfil,
        List<String> permissoes
) {
}
