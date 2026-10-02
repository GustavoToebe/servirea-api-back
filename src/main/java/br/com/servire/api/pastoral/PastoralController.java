package br.com.servire.api.pastoral;
import br.com.servire.api.pastoral.dto.PastoralDtos.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import java.util.*;
@RestController @RequestMapping("/pastorais")
public class PastoralController {
 private final PastoralService service;
 public PastoralController(PastoralService service){this.service=service;}
 @GetMapping("/equipes") @PreAuthorize("hasAuthority('PERM_PASTORAL')") public Pagina<Equipe> listar(@RequestParam(defaultValue="") String busca,@RequestParam(defaultValue="0") int pagina){return service.listar(busca,pagina);}
 @PostMapping("/equipes") @PreAuthorize("hasAuthority('PERM_PASTORAL_CRIAR')") public Equipe criar(@Valid @RequestBody SalvarEquipe req){return service.salvar(null,req);}
 @PutMapping("/equipes/{id}") @PreAuthorize("hasAuthority('PERM_PASTORAL_ALTERAR')") public Equipe alterar(@PathVariable UUID id,@Valid @RequestBody SalvarEquipe req){return service.salvar(id,req);}
 @GetMapping("/equipes/{id}/membros") @PreAuthorize("hasAuthority('PERM_PASTORAL')") public Pagina<Membro> membros(@PathVariable UUID id,@RequestParam(defaultValue="0") int pagina){return service.membros(id,pagina);}
 @PostMapping("/equipes/{id}/membros") @PreAuthorize("hasAuthority('PERM_PASTORAL_GERENCIAR')") public void membro(@PathVariable UUID id,@Valid @RequestBody SalvarMembro req){service.salvarMembro(id,req);}
 @GetMapping("/minhas-equipes") @PreAuthorize("hasAuthority('PERM_PASTORAL_COORDENACAO')") public List<Equipe> minhas(@RequestParam(defaultValue="0") int pagina){return service.minhasEquipes(pagina);}
 @GetMapping("/minhas-equipes/{id}/membros") @PreAuthorize("hasAuthority('PERM_PASTORAL_COORDENACAO')") public Pagina<Membro> proprios(@PathVariable UUID id,@RequestParam(defaultValue="0") int pagina){return service.membrosProprios(id,pagina);}
 @PutMapping("/minhas-equipes/{id}/membros/{pessoa}") @PreAuthorize("hasAuthority('PERM_PASTORAL_COORDENACAO_GERENCIAR')") public void proprio(@PathVariable UUID id,@PathVariable UUID pessoa,@Valid @RequestBody Ativo req){service.alterarMembroProprio(id,pessoa,req.ativo(),req.versao());}
 public record Ativo(@jakarta.validation.constraints.NotNull Boolean ativo,@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long versao){}
 @GetMapping("/pessoas") @PreAuthorize("hasAnyAuthority('PERM_PASTORAL_GERENCIAR','PERM_USUARIO_ALTERAR')") public List<PessoaOpcao> pessoas(@RequestParam String busca){return service.pessoas(busca);}
}
