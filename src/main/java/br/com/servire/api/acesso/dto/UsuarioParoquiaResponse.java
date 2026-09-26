package br.com.servire.api.acesso.dto;

import java.util.UUID;

public record UsuarioParoquiaResponse(
        UUID usuarioId,
        /** Número do usuário nesta paróquia (do vínculo, V038). */
        Long sequencial,
        String nome,
        String email,
        String tipoTelefone,
        String telefone,
        UUID perfilId,
        String perfilNome,
        boolean ativo,
        boolean senhaDefinida,
        boolean somenteLeitura,
        String situacaoAcesso
) {
}
