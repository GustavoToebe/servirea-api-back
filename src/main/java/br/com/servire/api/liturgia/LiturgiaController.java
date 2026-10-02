package br.com.servire.api.liturgia;
import br.com.servire.api.liturgia.dto.LiturgiaDtos.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import java.util.UUID;
@RestController @RequestMapping("/liturgia") public class LiturgiaController {
    private final LiturgiaService service;
    public LiturgiaController(LiturgiaService service) {
        this.service=service;
    }
    @GetMapping("/referencias") @PreAuthorize("hasAuthority('PERM_LITURGIA')") public Pagina<Referencia> referencias(@RequestParam(defaultValue="")String busca,@RequestParam(defaultValue="0")int pagina) {
        return service.referencias(busca,pagina);
    }
    @GetMapping("/roteiros") @PreAuthorize("hasAuthority('PERM_LITURGIA')") public Pagina<Roteiro> roteiros(@RequestParam(defaultValue="")String busca,@RequestParam(defaultValue="0")int pagina) {
        return service.roteiros(busca,pagina);
    }
    @PostMapping("/referencias") @PreAuthorize("hasAuthority('PERM_LITURGIA') and hasAuthority('PERM_LITURGIA_EDITAR')") public Referencia novaReferencia(@Valid @RequestBody ReferenciaSalvar r) {
        return service.salvarReferencia(null,r);
    }
    @PutMapping("/referencias/{id}") @PreAuthorize("hasAuthority('PERM_LITURGIA') and hasAuthority('PERM_LITURGIA_EDITAR')") public Referencia editarReferencia(@PathVariable UUID id,@Valid @RequestBody ReferenciaSalvar r) {
        return service.salvarReferencia(id,r);
    }
    @PostMapping("/roteiros") @PreAuthorize("hasAuthority('PERM_LITURGIA') and hasAuthority('PERM_LITURGIA_EDITAR')") public Roteiro novoRoteiro(@Valid @RequestBody RoteiroSalvar r) {
        return service.salvarRoteiro(null,r);
    }
    @PutMapping("/roteiros/{id}") @PreAuthorize("hasAuthority('PERM_LITURGIA') and hasAuthority('PERM_LITURGIA_EDITAR')") public Roteiro editarRoteiro(@PathVariable UUID id,@Valid @RequestBody RoteiroSalvar r) {
        return service.salvarRoteiro(id,r);
    }
}
