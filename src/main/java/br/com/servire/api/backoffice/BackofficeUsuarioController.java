package br.com.servire.api.backoffice;

import br.com.servire.api.backoffice.dto.AtualizarUsuarioRequest;
import br.com.servire.api.backoffice.dto.CriarUsuarioRequest;
import br.com.servire.api.backoffice.dto.SubstituirVinculosRequest;
import br.com.servire.api.backoffice.dto.UsuarioAdminResponse;
import br.com.servire.api.backoffice.dto.VinculoParoquiaResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/usuarios")
@PreAuthorize("hasAuthority('PERM_BACKOFFICE')")
public class BackofficeUsuarioController {

    private final BackofficeUsuarioService backofficeUsuarioService;

    public BackofficeUsuarioController(BackofficeUsuarioService backofficeUsuarioService) {
        this.backofficeUsuarioService = backofficeUsuarioService;
    }

    @GetMapping
    public List<UsuarioAdminResponse> listar(@RequestParam(required = false) Boolean ativo,
                                              @RequestParam(required = false) String busca) {
        return backofficeUsuarioService.listar(ativo, busca).stream()
                .map(u -> UsuarioAdminResponse.de(u, backofficeUsuarioService.vinculosDe(u.getId())))
                .toList();
    }

    @PostMapping
    public UsuarioAdminResponse criar(@RequestBody @Valid CriarUsuarioRequest request) {
        var usuario = backofficeUsuarioService.criar(request);
        return UsuarioAdminResponse.de(usuario, backofficeUsuarioService.vinculosDe(usuario.getId()));
    }

    @GetMapping("/{id}")
    public UsuarioAdminResponse buscar(@PathVariable UUID id) {
        return UsuarioAdminResponse.de(
                backofficeUsuarioService.buscar(id),
                backofficeUsuarioService.vinculosDe(id));
    }

    @PutMapping("/{id}")
    public UsuarioAdminResponse atualizar(@PathVariable UUID id,
                                           @RequestBody @Valid AtualizarUsuarioRequest request) {
        var usuario = backofficeUsuarioService.atualizar(id, request);
        return UsuarioAdminResponse.de(usuario, backofficeUsuarioService.vinculosDe(usuario.getId()));
    }

    @PutMapping("/{id}/paroquias")
    public List<VinculoParoquiaResponse> substituirVinculos(@PathVariable UUID id,
                                                             @RequestBody @Valid SubstituirVinculosRequest request) {
        return backofficeUsuarioService.substituirVinculos(id, request.vinculos()).stream()
                .map(VinculoParoquiaResponse::de)
                .toList();
    }
}
