package br.com.servire.api.integracao;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;
@RestController
public class ConsumoInstanciaController {
 private final ConsumoInstanciaService service;
 public ConsumoInstanciaController(ConsumoInstanciaService service) {this.service=service;}
 @GetMapping("/integracao/v1/instancias/{id}/consumo") @PreAuthorize("hasAuthority('PERM_INTEGRACAO')")
 public ConsumoInstanciaService.Resposta consultar(@PathVariable UUID id) {return service.consultar(id);}
}
