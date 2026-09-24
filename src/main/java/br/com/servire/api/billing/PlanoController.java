package br.com.servire.api.billing;

import br.com.servire.api.billing.dto.NovoPrecoRequest;
import br.com.servire.api.billing.dto.PlanoRequest;
import br.com.servire.api.billing.dto.PlanoResponse;
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

/** Catálogo de planos no painel (seção 63). Só o operador ({@code PERM_BACKOFFICE}). */
@RestController
@RequestMapping("/admin/planos")
@PreAuthorize("hasAuthority('PERM_BACKOFFICE')")
public class PlanoController {

    private final PlanoService planoService;

    public PlanoController(PlanoService planoService) {
        this.planoService = planoService;
    }

    @GetMapping
    public List<PlanoResponse> listar() {
        return planoService.listar();
    }

    @PostMapping
    public PlanoResponse criar(@RequestBody @Valid PlanoRequest request) {
        return planoService.criar(request);
    }

    @PutMapping("/{id}")
    public PlanoResponse atualizar(@PathVariable UUID id, @RequestBody @Valid PlanoRequest request) {
        return planoService.atualizar(id, request);
    }

    @PostMapping("/{id}/precos")
    public PlanoResponse adicionarPreco(@PathVariable UUID id, @RequestBody @Valid NovoPrecoRequest request) {
        return planoService.adicionarPreco(id, request);
    }
}
