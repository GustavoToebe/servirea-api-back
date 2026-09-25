package br.com.servire.api.acesso;

import br.com.servire.api.web.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Ninguém concede mais do que tem (25/09/2026). Sem esta trava, quem só
 * tinha {@code PERFIL_ALTERAR} marcava "Liberar todas as opções" no próprio
 * perfil, e quem só tinha {@code USUARIO_ALTERAR} se movia para o perfil
 * Administrador.
 *
 * <p>Regras para quem <b>não</b> tem acesso total:</p>
 * <ul>
 *   <li>não cria nem edita perfil com acesso total;</li>
 *   <li>só marca permissões que a própria sessão tem;</li>
 *   <li>não coloca usuário num perfil que tenha algo além disso, nem
 *   mexe em usuário que está num perfil com acesso total.</li>
 * </ul>
 *
 * <p>Sem autenticação (chamada interna: provisionamento pela Central,
 * testes de serviço) a trava não se aplica.</p>
 */
final class ConcessaoDePermissao {

    private ConcessaoDePermissao() {
    }

    static void exigirPodeConceder(boolean acessoTotal, Collection<String> codigos) {
        Set<String> daSessao = daSessao();
        if (daSessao == null || daSessao.contains(PermissoesDaSessao.ACESSO_TOTAL)) {
            return;
        }
        if (acessoTotal) {
            throw new ForbiddenException("Só quem tem acesso total pode conceder acesso total.");
        }
        for (String codigo : codigos) {
            if (!daSessao.contains("PERM_" + codigo)) {
                throw new ForbiddenException("Você não pode conceder uma permissão que não tem.");
            }
        }
    }

    static void exigirPodeAlterarAcessoTotal(boolean alvoTemAcessoTotal) {
        Set<String> daSessao = daSessao();
        if (alvoTemAcessoTotal && daSessao != null && !daSessao.contains(PermissoesDaSessao.ACESSO_TOTAL)) {
            throw new ForbiddenException("Só quem tem acesso total altera quem tem acesso total.");
        }
    }

    private static Set<String> daSessao() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Set<String> codigos = new HashSet<>();
        for (GrantedAuthority authority : auth.getAuthorities()) {
            codigos.add(authority.getAuthority());
        }
        return codigos;
    }
}
