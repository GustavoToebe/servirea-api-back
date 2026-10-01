package br.com.servire.api.acesso;
import org.springframework.security.core.context.SecurityContextHolder;
import br.com.servire.api.web.ForbiddenException;
import java.util.Collection;

/** Dados de acolhimento nunca são liberados pela permissão genérica de cadastro. */
public final class PermissaoCuidados {
    private PermissaoCuidados() { }
    public static boolean ler() { return possui("PERM_PESSOA_CUIDADOS_LER"); }
    private static boolean possui(String codigo) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(codigo));
    }
    /** Serviços internos sem sessão (aprovação/jobs) preservam as regras do fluxo; HTTP sempre exige autenticação. */
    public static boolean alterarSePermitido(Collection<?> condicoes,Integer nivel,String outra,String cuidados) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth==null || (ler() && possui("PERM_PESSOA_CUIDADOS_ALTERAR"))) return true;
        if ((condicoes!=null && !condicoes.isEmpty()) || nivel!=null || (outra!=null && !outra.isBlank()) || (cuidados!=null && !cuidados.isBlank()))
            throw new ForbiddenException("Seu perfil não pode alterar dados de cuidado e acolhimento.");
        return false; // Dados omitidos por um editor geral NÃO apagam o conteúdo protegido.
    }
}
