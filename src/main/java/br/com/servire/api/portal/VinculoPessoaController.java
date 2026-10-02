package br.com.servire.api.portal;
import br.com.servire.api.acesso.VinculoPessoaService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;
@RestController
public class VinculoPessoaController {
 private final VinculoPessoaService service;
 public VinculoPessoaController(VinculoPessoaService service){this.service=service;}
 @GetMapping("/usuarios/{id}/pessoa") @PreAuthorize("hasAuthority('PERM_USUARIO_ALTERAR')") public VinculoPessoaService.Vinculo buscar(@PathVariable UUID id){return service.buscar(id);}
 @PutMapping("/usuarios/{id}/pessoa") @PreAuthorize("hasAuthority('PERM_USUARIO_ALTERAR')") public VinculoPessoaService.Vinculo salvar(@PathVariable UUID id,@RequestBody VinculoPessoaService.Vinculo req){return service.salvar(id,req.pessoaId());}
}
