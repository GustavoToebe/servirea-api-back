package br.com.servire.api.portal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
@RestController
public class AcessosDependentesController {
 private final AcessosDependentesService acessos;private final PortalService portal;private final RespostaEscalaService respostas;
 public AcessosDependentesController(AcessosDependentesService acessos,PortalService portal,RespostaEscalaService respostas){this.acessos=acessos;this.portal=portal;this.respostas=respostas;}
 @GetMapping("/portal/dependentes") @PreAuthorize("hasAuthority('PERM_PORTAL_DEPENDENTES')")
 public List<AcessosDependentesService.Dependente> dependentes(@RequestParam(defaultValue="0") int pagina){return acessos.dependentes(pagina);}
 @GetMapping("/portal/dependentes/{id}/compromissos") @PreAuthorize("hasAuthority('PERM_PORTAL_DEPENDENTES')")
 public PortalService.Resposta compromissos(@PathVariable UUID id,@RequestParam LocalDate de,@RequestParam LocalDate ate){return portal.consultarDependente(id,de,ate);}
 @PutMapping("/portal/dependentes/{pessoa}/vagas/{vaga}/resposta") @PreAuthorize("hasAuthority('PERM_PORTAL_DEPENDENTES') and hasAuthority('PERM_PORTAL_RESPONDER')")
 public br.com.servire.api.portal.dto.RespostaEscalaDtos.Atual responder(@PathVariable UUID pessoa,@PathVariable UUID vaga,@Valid @RequestBody br.com.servire.api.portal.dto.RespostaEscalaDtos.Responder req){return respostas.responderDependente(pessoa,vaga,req);}
 @GetMapping("/pessoas/{responsavel}/acessos-dependentes") @PreAuthorize("hasAuthority('PERM_PESSOA')")
 public List<AcessosDependentesService.Autorizacao> autorizacoes(@PathVariable UUID responsavel,@RequestParam(defaultValue="0") int pagina){return acessos.listar(responsavel,pagina);}
 @PutMapping("/pessoas/{responsavel}/acessos-dependentes/{dependente}") @PreAuthorize("hasAuthority('PERM_PESSOA_ALTERAR')")
 public void autorizar(@PathVariable UUID responsavel,@PathVariable UUID dependente,@Valid @RequestBody Autorizar req){acessos.autorizar(responsavel,dependente,req.consulta(),req.resposta(),req.versao());}
 public record Autorizar(@NotNull Boolean consulta,@NotNull Boolean resposta,@NotNull @PositiveOrZero Long versao){}
}
