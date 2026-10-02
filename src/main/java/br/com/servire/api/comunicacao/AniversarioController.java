package br.com.servire.api.comunicacao;
import br.com.servire.api.comunicacao.dto.AniversarioDtos.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import java.util.*;
@RestController @RequestMapping("/aniversarios")
public class AniversarioController {
 private final AniversarioService service;public AniversarioController(AniversarioService service){this.service=service;}
 @GetMapping("/configuracoes") @PreAuthorize("hasAuthority('PERM_ANIVERSARIO')") public List<Config> configuracoes(){return service.configuracoes();}
 @GetMapping("/layouts") @PreAuthorize("hasAuthority('PERM_ANIVERSARIO')") public List<OpcaoLayout> layouts(){return service.layouts();}
 @PutMapping("/configuracoes/{canal}") @PreAuthorize("hasAuthority('PERM_ANIVERSARIO') and hasAuthority('PERM_ANIVERSARIO_CONFIGURAR')") public Config configurar(@PathVariable TipoEnvio canal,@Valid @RequestBody Configurar req){return service.configurar(canal,req);}
 @GetMapping("/pessoas/{id}") @PreAuthorize("hasAuthority('PERM_ANIVERSARIO') and hasAuthority('PERM_PESSOA')") public List<Autorizacao> autorizacoes(@PathVariable UUID id){return service.autorizacoes(id);}
 @PutMapping("/pessoas/{id}/{canal}") @PreAuthorize("hasAuthority('PERM_ANIVERSARIO') and hasAuthority('PERM_PESSOA') and hasAuthority('PERM_ANIVERSARIO_AUTORIZAR')") public Autorizacao autorizar(@PathVariable UUID id,@PathVariable TipoEnvio canal,@Valid @RequestBody Autorizar req){return service.autorizar(id,canal,req);}
}
