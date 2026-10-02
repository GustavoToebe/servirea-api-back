package br.com.servire.api.portal;
import br.com.servire.api.portal.dto.DisponibilidadeDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/portal/indisponibilidades")
public class DisponibilidadePortalController {
 private final DisponibilidadePortalService service;
 public DisponibilidadePortalController(DisponibilidadePortalService service){this.service=service;}
 @GetMapping @PreAuthorize("hasAuthority('PERM_PORTAL_VOLUNTARIO')") public Resposta buscar(@RequestParam int ano,@RequestParam int mes){return service.buscar(ano,mes);}
 @PutMapping @PreAuthorize("hasAuthority('PERM_PORTAL_DISPONIBILIDADE')") public Resposta salvar(@RequestParam int ano,@RequestParam int mes,@Valid @RequestBody Salvar req){return service.salvar(ano,mes,req);}
}
