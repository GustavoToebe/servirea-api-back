package br.com.servire.api.acesso;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/permissoes")
public class PermissaoCatalogoController {

    @GetMapping("/catalogo")
    @PreAuthorize("hasAnyAuthority('PERM_PERFIL','PERM_USUARIO')")
    public List<CatalogoPermissao.Secao> catalogo() {
        return CatalogoPermissao.SECOES;
    }
}
