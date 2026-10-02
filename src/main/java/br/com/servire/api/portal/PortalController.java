package br.com.servire.api.portal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;
@RestController
public class PortalController {
 private final PortalService portal;
 public PortalController(PortalService portal){this.portal=portal;}
 @GetMapping("/portal/compromissos") @PreAuthorize("hasAuthority('PERM_PORTAL_VOLUNTARIO')")
 public PortalService.Resposta consultar(@RequestParam LocalDate de,@RequestParam LocalDate ate){return portal.consultar(de,ate);}
}
