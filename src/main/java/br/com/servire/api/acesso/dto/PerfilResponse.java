package br.com.servire.api.acesso.dto;

import br.com.servire.api.acesso.Perfil;

import java.util.List;
import java.util.UUID;

public record PerfilResponse(
        UUID id,
        Long sequencial,
        String nome,
        boolean ativo,
        boolean acessoTotal,
        boolean sistema,
        long usuarios,
        List<String> permissoes
) {
    public static PerfilResponse de(Perfil perfil, long usuarios) {
        List<String> codigos = perfil.getPermissoes().stream()
                .map(item -> item.getPermissao())
                .sorted()
                .toList();
        return new PerfilResponse(
                perfil.getId(),
                perfil.getSequencial(),
                perfil.getNome(),
                perfil.isAtivo(),
                perfil.isAcessoTotal(),
                perfil.isSistema(),
                usuarios,
                codigos);
    }
}
