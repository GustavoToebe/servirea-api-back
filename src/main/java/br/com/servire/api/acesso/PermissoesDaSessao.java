package br.com.servire.api.acesso;

import br.com.servire.api.auth.UsuarioTenant;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Traduz o perfil da paróquia nas authorities da requisição:
 * {@code PERM_<código do catálogo>} para cada permissão, mais
 * {@code ROLE_ADMIN} (auditoria) ou {@code ROLE_PERFIL}.
 *
 * <p>Os endpoints pedem a ação específica ({@code PERM_ESCALA_EXCLUIR},
 * {@code PERM_VAGA_PRESENCA} …) e o módulo libera as leituras. Até
 * 25/09/2026 existia uma "ponte" que transformava qualquer ação do módulo
 * na permissão antiga de escrita inteira ({@code ESCALA_WRITE}): quem só
 * registrava presença podia excluir escala. A ponte saiu junto com o enum
 * antigo {@code Permissao}; o catálogo ({@link CatalogoPermissao}) é a
 * única lista.</p>
 *
 * <p>Vínculo sem perfil ({@code perfil_id} nulo — só teste e dado anterior
 * à V035) cai na role antiga, traduzida para o catálogo em
 * {@link #daRole}.</p>
 */
public final class PermissoesDaSessao {

    /** Marca quem tem acesso total. Usada pela trava de concessão ({@link ConcessaoDePermissao}). */
    public static final String ACESSO_TOTAL = "ACESSO_TOTAL";

    private static final Set<String> LEITURA = Set.of("PESSOA", "ESCALA", "VAGA", "INSCRICAO");

    private PermissoesDaSessao() {
    }

    /** Sessão de suporte: o mesmo conjunto de quem tem acesso total. */
    public static List<GrantedAuthority> acessoTotal() {
        return montar(CatalogoPermissao.codigos(), true);
    }

    public static List<GrantedAuthority> de(UsuarioTenant vinculo) {
        Perfil perfil = vinculo.getPerfil();
        if (perfil == null) {
            return daRole(vinculo.getRole());
        }
        return doPerfil(perfil);
    }

    public static List<GrantedAuthority> doPerfil(Perfil perfil) {
        if (perfil.isAcessoTotal()) {
            return acessoTotal();
        }
        Set<String> codigos = new LinkedHashSet<>();
        for (PerfilPermissao item : perfil.getPermissoes()) {
            if (CatalogoPermissao.codigos().contains(item.getPermissao())) {
                codigos.add(item.getPermissao());
            }
        }
        return montar(codigos, false);
    }

    /** Role antiga → catálogo. ADMIN = acesso total; COORDENADOR = operação; VISUALIZADOR = leitura. */
    public static List<GrantedAuthority> daRole(UsuarioTenant.Role role) {
        if (role == UsuarioTenant.Role.ADMIN) {
            return acessoTotal();
        }
        Set<String> codigos = new LinkedHashSet<>(LEITURA);
        if (role == UsuarioTenant.Role.COORDENADOR) {
            for (String codigo : CatalogoPermissao.codigos()) {
                String modulo = CatalogoPermissao.moduloDe(codigo);
                if (modulo != null && LEITURA.contains(modulo)) {
                    codigos.add(codigo);
                }
            }
        }
        return montar(codigos, false);
    }

    private static List<GrantedAuthority> montar(Set<String> codigos, boolean total) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        boolean auditoria = total || codigos.contains("AUDITORIA");
        authorities.add(new SimpleGrantedAuthority(auditoria ? "ROLE_ADMIN" : "ROLE_PERFIL"));
        if (total) {
            authorities.add(new SimpleGrantedAuthority(ACESSO_TOTAL));
        }
        for (String codigo : codigos) {
            authorities.add(new SimpleGrantedAuthority("PERM_" + codigo));
        }
        return authorities;
    }
}
