package br.com.servire.api.acesso;

import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.security.Permissao;
import br.com.servire.api.security.RolePermissoes;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Traduz o perfil (ou, na falta dele, a role antiga) nas authorities que
 * os controllers já conhecem. Os endpoints antigos continuam em
 * {@code PERM_VOLUNTARIO_*} / {@code PERM_ESCALA_*}; o catálogo novo
 * ganha uma ponte para esses nomes.
 */
public final class PermissoesDaSessao {

    private PermissoesDaSessao() {
    }

    /** Sessão de suporte: o mesmo conjunto de quem tem acesso total. */
    public static List<GrantedAuthority> acessoTotal() {
        return doPerfil(new Perfil(null, "suporte", true, true));
    }

    public static List<GrantedAuthority> de(UsuarioTenant vinculo) {
        Perfil perfil = vinculo.getPerfil();
        if (perfil == null) {
            return daRole(vinculo.getRole());
        }
        return doPerfil(perfil);
    }

    public static List<GrantedAuthority> doPerfil(Perfil perfil) {
        Set<String> codigos = new LinkedHashSet<>();
        if (perfil.isAcessoTotal()) {
            for (Permissao permissao : Permissao.values()) {
                if (permissao != Permissao.BACKOFFICE) {
                    codigos.add(permissao.name());
                }
            }
        } else {
            for (PerfilPermissao item : perfil.getPermissoes()) {
                codigos.add(item.getPermissao());
            }
        }
        codigos.addAll(ponteLegada(codigos));
        List<GrantedAuthority> authorities = new ArrayList<>();
        boolean auditoria = perfil.isAcessoTotal() || codigos.contains("AUDITORIA");
        authorities.add(new SimpleGrantedAuthority(auditoria ? "ROLE_ADMIN" : "ROLE_PERFIL"));
        for (String codigo : codigos) {
            authorities.add(new SimpleGrantedAuthority("PERM_" + codigo));
        }
        return authorities;
    }

    private static List<GrantedAuthority> daRole(UsuarioTenant.Role role) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        for (Permissao permissao : RolePermissoes.de(role)) {
            authorities.add(new SimpleGrantedAuthority("PERM_" + permissao.name()));
        }
        return authorities;
    }

    static Set<String> ponteLegada(Set<String> codigos) {
        Set<String> extra = new LinkedHashSet<>();
        if (codigos.contains("PESSOA") || contemPrefixo(codigos, "PESSOA_")) {
            extra.add("VOLUNTARIO_READ");
        }
        if (codigos.contains("PESSOA_CRIAR") || codigos.contains("PESSOA_ALTERAR")
                || codigos.contains("PESSOA_EXCLUIR") || codigos.contains("PESSOA_ATIVAR_INATIVAR")) {
            extra.add("VOLUNTARIO_WRITE");
        }
        if (codigos.contains("ESCALA") || codigos.contains("VAGA")) {
            extra.add("ESCALA_READ");
        }
        if (contemPrefixo(codigos, "ESCALA_") || codigos.contains("VAGA_ALOCAR") || codigos.contains("VAGA_PRESENCA")) {
            extra.add("ESCALA_WRITE");
        }
        if (codigos.contains("INSCRICAO")) {
            extra.add("INSCRICAO_READ");
        }
        if (codigos.contains("INSCRICAO_ALTERAR") || codigos.contains("INSCRICAO_APROVAR")
                || codigos.contains("INSCRICAO_REJEITAR")) {
            extra.add("INSCRICAO_APPROVE");
        }
        if (codigos.contains("PAROQUIA_ALTERAR")) {
            extra.add("CONFIG_WRITE");
        }
        return extra;
    }

    private static boolean contemPrefixo(Set<String> codigos, String prefixo) {
        for (String codigo : codigos) {
            if (codigo.startsWith(prefixo)) {
                return true;
            }
        }
        return false;
    }
}
