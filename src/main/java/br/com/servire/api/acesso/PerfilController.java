package br.com.servire.api.acesso;

import br.com.servire.api.acesso.dto.PerfilRequest;
import br.com.servire.api.acesso.dto.PerfilResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/perfis")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_PERFIL','PERM_USUARIO')")
    public List<PerfilResponse> listar() {
        return perfilService.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_PERFIL','PERM_USUARIO')")
    public PerfilResponse buscar(@PathVariable UUID id) {
        return perfilService.buscar(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_PERFIL_CRIAR')")
    public PerfilResponse criar(@RequestBody @Valid PerfilRequest request) {
        return perfilService.criar(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PERFIL_ALTERAR')")
    public PerfilResponse atualizar(@PathVariable UUID id, @RequestBody @Valid PerfilRequest request) {
        return perfilService.atualizar(id, request);
    }

    @PostMapping("/{id}/duplicar")
    @PreAuthorize("hasAuthority('PERM_PERFIL_CRIAR')")
    public PerfilResponse duplicar(@PathVariable UUID id) {
        return perfilService.duplicar(id);
    }
}
