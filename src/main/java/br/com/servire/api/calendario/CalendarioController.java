package br.com.servire.api.calendario;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.*;
@RestController
public class CalendarioController {
 private final CalendarioService service;
 public CalendarioController(CalendarioService service){this.service=service;}
 @PostMapping("/calendario/assinatura") @PreAuthorize("hasAuthority('PERM_CALENDARIO') and hasAuthority('PERM_PORTAL_VOLUNTARIO')") public CalendarioService.Criada criar(){return service.criar();}
 @DeleteMapping("/calendario/assinatura") @PreAuthorize("hasAuthority('PERM_CALENDARIO') and hasAuthority('PERM_PORTAL_VOLUNTARIO')") public void revogar(){service.revogar();}
 @GetMapping(value="/public/calendario/{token}.ics",produces="text/calendar;charset=UTF-8") public ResponseEntity<String> feed(@PathVariable String token){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").header("X-Robots-Tag","noindex, nofollow").body(service.feed(token));}
}
