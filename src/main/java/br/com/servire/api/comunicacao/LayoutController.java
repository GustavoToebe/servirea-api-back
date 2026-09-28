package br.com.servire.api.comunicacao;

import br.com.servire.api.comunicacao.dto.LayoutRequest;
import br.com.servire.api.comunicacao.dto.LayoutResponse;
import br.com.servire.api.comunicacao.dto.PreVisualizacaoResponse;
import br.com.servire.api.comunicacao.dto.PreVisualizarRequest;
import br.com.servire.api.comunicacao.dto.TagResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/**
 * Endpoints de layouts de envio (PLANO-004).
 * Todas as rotas exigem autenticação e permissão do catálogo.
 */
@RestController
@RequestMapping("/layouts")
public class LayoutController {

    private final LayoutService layoutService;

    public LayoutController(LayoutService layoutService) {
        this.layoutService = layoutService;
    }

    @PreAuthorize("hasAuthority('PERM_LAYOUT')")
    @GetMapping
    public List<LayoutResponse> listar(
            @RequestParam(required = false) TipoLayout tipoLayout,
            @RequestParam(required = false) TipoEnvio tipoEnvio,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) String nome) {
        return layoutService.listar(tipoLayout, tipoEnvio, ativo, nome)
                .stream().map(LayoutResponse::de).toList();
    }

    @PreAuthorize("hasAuthority('PERM_LAYOUT')")
    @GetMapping("/{id}")
    public LayoutResponse buscarPorId(@PathVariable UUID id) {
        return LayoutResponse.de(layoutService.buscarPorId(id));
    }

    @PreAuthorize("hasAuthority('PERM_LAYOUT_CRIAR')")
    @PostMapping
    public LayoutResponse criar(@RequestBody @Valid LayoutRequest request) {
        return LayoutResponse.de(layoutService.criar(request));
    }

    @PreAuthorize("hasAuthority('PERM_LAYOUT_ALTERAR')")
    @PutMapping("/{id}")
    public LayoutResponse atualizar(@PathVariable UUID id, @RequestBody @Valid LayoutRequest request) {
        return LayoutResponse.de(layoutService.atualizar(id, request));
    }

    @PreAuthorize("hasAuthority('PERM_LAYOUT_EXCLUIR')")
    @DeleteMapping("/{id}")
    public void excluir(@PathVariable UUID id) {
        layoutService.excluir(id);
    }

    @PreAuthorize("hasAuthority('PERM_LAYOUT')")
    @GetMapping("/tags")
    public List<TagResponse> tags(@RequestParam TipoLayout tipoLayout) {
        return layoutService.tags(tipoLayout);
    }

    @PreAuthorize("hasAuthority('PERM_LAYOUT')")
    @PostMapping("/pre-visualizar")
    public PreVisualizacaoResponse preVisualizar(@RequestBody @Valid PreVisualizarRequest request) {
        return layoutService.preVisualizar(request);
    }
}
