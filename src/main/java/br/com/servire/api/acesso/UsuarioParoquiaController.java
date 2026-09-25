package br.com.servire.api.acesso;

import br.com.servire.api.acesso.dto.UsuarioParoquiaRequest;
import br.com.servire.api.acesso.dto.UsuarioParoquiaResponse;
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
@RequestMapping("/usuarios")
public class UsuarioParoquiaController {

    private final UsuarioParoquiaService service;

    public UsuarioParoquiaController(UsuarioParoquiaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_USUARIO','PERM_USUARIO_ALTERAR')")
    public List<UsuarioParoquiaResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_USUARIO','PERM_USUARIO_ALTERAR')")
    public UsuarioParoquiaResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_USUARIO_CRIAR')")
    public UsuarioParoquiaResponse criar(@RequestBody @Valid UsuarioParoquiaRequest request) {
        return service.criar(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_USUARIO_ALTERAR')")
    public UsuarioParoquiaResponse atualizar(@PathVariable UUID id, @RequestBody @Valid UsuarioParoquiaRequest request) {
        return service.atualizar(id, request);
    }

    @PostMapping("/{id}/convite")
    @PreAuthorize("hasAuthority('PERM_USUARIO_REENVIAR_CONVITE')")
    public void reenviar(@PathVariable UUID id) {
        service.reenviarConvite(id);
    }
}
