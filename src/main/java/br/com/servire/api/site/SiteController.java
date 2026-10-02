package br.com.servire.api.site;
import br.com.servire.api.site.dto.SiteDtos.*;
import br.com.servire.api.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
@RestController
public class SiteController {
 private final SiteService service;private final SitePublicoLimiter limiter;public SiteController(SiteService service,SitePublicoLimiter limiter){this.service=service;this.limiter=limiter;}
 @GetMapping("/site-paroquia") @PreAuthorize("hasAuthority('PERM_SITE')") public Estado consultar(){return service.consultar();}
 @PutMapping("/site-paroquia") @PreAuthorize("hasAuthority('PERM_SITE') and hasAuthority('PERM_SITE_EDITAR')") public Estado salvar(@Valid @RequestBody Salvar req){return service.salvar(req);}
 @PostMapping("/site-paroquia/publicar") @PreAuthorize("hasAuthority('PERM_SITE') and hasAuthority('PERM_SITE_PUBLICAR')") public Estado publicar(@Valid @RequestBody Publicar req){return service.publicar(req);}
 @PostMapping("/site-paroquia/despublicar") @PreAuthorize("hasAuthority('PERM_SITE') and hasAuthority('PERM_SITE_PUBLICAR')") public Estado despublicar(@Valid @RequestBody Revisao req){return service.despublicar(req);}
 @GetMapping("/public/paroquias/{slug}") @PreAuthorize("permitAll()") public ResponseEntity<Dados> publico(@PathVariable String slug,HttpServletRequest req){limiter.consultar(ClientIp.de(req));return ResponseEntity.ok().header("Cache-Control","no-store").body(service.publico(slug));}
}
