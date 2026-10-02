package br.com.servire.api.mural;
import br.com.servire.api.mural.dto.AvisoDtos.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import java.util.UUID;
@RestController @RequestMapping("/mural/avisos")
public class AvisoController {
 private final AvisoService service;
 public AvisoController(AvisoService service) {this.service=service;}
 @GetMapping @PreAuthorize("hasAuthority('PERM_MURAL')")
 public Pagina listar(@RequestParam(required=false) String busca,@RequestParam(required=false) Aviso.Status status,
       @RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="30") int tamanho) {return service.listar(busca,status,pagina,tamanho);}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('PERM_MURAL')") public Resposta buscar(@PathVariable UUID id) {return service.buscar(id);}
 @PostMapping @PreAuthorize("hasAuthority('PERM_MURAL_CRIAR')") public Resposta criar(@Valid @RequestBody Salvar req) {return service.salvar(null,req);}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('PERM_MURAL_ALTERAR')") public Resposta alterar(@PathVariable UUID id,@Valid @RequestBody Salvar req) {return service.salvar(id,req);}
}
