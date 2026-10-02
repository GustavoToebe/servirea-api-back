package br.com.servire.api.estoque;
import br.com.servire.api.estoque.dto.EstoqueDtos.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import java.util.*;
@RestController @RequestMapping("/estoque")public class EstoqueController {
    private final EstoqueService service;
    public EstoqueController(EstoqueService service) {
        this.service=service;
    }
    @GetMapping @PreAuthorize("hasAuthority('PERM_ESTOQUE')")public Pagina<Item> listar(@RequestParam(defaultValue="")String busca,@RequestParam(defaultValue="0")int pagina) {
        return service.listar(busca,pagina);
    }
    @GetMapping("/responsaveis") @PreAuthorize("hasAuthority('PERM_ESTOQUE')")public List<Responsavel> responsaveis(@RequestParam(defaultValue="")String busca) {
        return service.responsaveis(busca);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('PERM_ESTOQUE')")public Item buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }
    @GetMapping("/{id}/movimentos") @PreAuthorize("hasAuthority('PERM_ESTOQUE')")public Pagina<Movimento> historico(@PathVariable UUID id,@RequestParam(defaultValue="0")int pagina) {
        return service.historico(id,pagina);
    }
    @PostMapping @PreAuthorize("hasAuthority('PERM_ESTOQUE') and hasAuthority('PERM_ESTOQUE_EDITAR')")public Item criar(@Valid @RequestBody Salvar r) {
        return service.salvar(null,r);
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('PERM_ESTOQUE') and hasAuthority('PERM_ESTOQUE_EDITAR')")public Item editar(@PathVariable UUID id,@Valid @RequestBody Salvar r) {
        return service.salvar(id,r);
    }
    @PostMapping("/{id}/movimentos") @PreAuthorize("hasAuthority('PERM_ESTOQUE') and hasAuthority('PERM_ESTOQUE_MOVIMENTAR') and (#r.tipo() != 'AJUSTE' or hasAuthority('PERM_ESTOQUE_AJUSTAR'))")public Movimento movimentar(@PathVariable UUID id,@Valid @RequestBody Movimentar r) {
        return service.movimentar(id,r);
    }
}
