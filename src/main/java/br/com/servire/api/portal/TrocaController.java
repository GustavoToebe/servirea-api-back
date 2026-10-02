package br.com.servire.api.portal;
import br.com.servire.api.portal.dto.TrocaDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.*;
@RestController
public class TrocaController {
 private final TrocaService service;public TrocaController(TrocaService service){this.service=service;}
 @GetMapping("/portal/trocas/substitutos") @PreAuthorize("hasAuthority('PERM_PORTAL_TROCAR')")
 public List<Substituto> substitutos(@RequestParam String busca){return service.substitutos(busca);}
 @GetMapping("/portal/trocas") @PreAuthorize("hasAuthority('PERM_PORTAL_VOLUNTARIO')")
 public Pagina<Pedido> minhas(@RequestParam(defaultValue="0") int pagina){return service.minhas(pagina);}
 @PostMapping("/portal/vagas/{id}/trocas") @PreAuthorize("hasAuthority('PERM_PORTAL_TROCAR')")
 public Pedido solicitar(@PathVariable UUID id,@Valid @RequestBody Solicitar req){return service.solicitar(id,req);}
 @PutMapping("/portal/trocas/{id}/aceite") @PreAuthorize("hasAuthority('PERM_PORTAL_TROCAR')")
 public Pedido responder(@PathVariable UUID id,@Valid @RequestBody Decisao req){return service.responder(id,req);}
 @PutMapping("/portal/trocas/{id}/cancelamento") @PreAuthorize("hasAuthority('PERM_PORTAL_TROCAR')")
 public Pedido cancelar(@PathVariable UUID id,@Valid @RequestBody Versao req){return service.cancelar(id,req);}
 @GetMapping("/escalas/{id}/trocas") @PreAuthorize("hasAuthority('PERM_VAGA_TROCA_LER')")
 public Pagina<Pedido> coordenacao(@PathVariable UUID id,@RequestParam(defaultValue="0") int pagina){return service.coordenacao(id,pagina);}
 @PutMapping("/escalas/trocas/{id}/decisao") @PreAuthorize("hasAuthority('PERM_VAGA_TROCA_DECIDIR')")
 public Pedido decidir(@PathVariable UUID id,@Valid @RequestBody Decisao req){return service.decidir(id,req);}
}
