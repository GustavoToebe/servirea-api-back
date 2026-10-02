package br.com.servire.api.tarefas;
import br.com.servire.api.tarefas.dto.TarefaDtos.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import java.util.UUID;
@RestController @RequestMapping("/tarefas")
public class TarefaController {
 private final TarefaService service;
 public TarefaController(TarefaService service) {this.service=service;}
 @GetMapping @PreAuthorize("hasAuthority('PERM_TAREFA')")
 public Pagina listar(@RequestParam(required=false) String busca,@RequestParam(required=false) Tarefa.Status status,
       @RequestParam(required=false) UUID responsavelUsuarioId,@RequestParam(defaultValue="false") boolean minhas,
       @RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="30") int tamanho) {return service.listar(busca,status,responsavelUsuarioId,minhas,pagina,tamanho);}
 @GetMapping("/responsaveis") @PreAuthorize("hasAuthority('PERM_TAREFA')") public java.util.List<Responsavel> responsaveis(@RequestParam(defaultValue="") String busca) {return service.responsaveis(busca);}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('PERM_TAREFA')") public Resposta buscar(@PathVariable UUID id) {return service.buscar(id);}
 @PostMapping @PreAuthorize("hasAuthority('PERM_TAREFA_CRIAR')") public Resposta criar(@Valid @RequestBody Salvar req) {return service.salvar(null,req);}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('PERM_TAREFA_ALTERAR')") public Resposta alterar(@PathVariable UUID id,@Valid @RequestBody Salvar req) {return service.salvar(id,req);}
}
