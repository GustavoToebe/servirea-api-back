package br.com.servire.api.relatorios;

import br.com.servire.api.escala.*;
import br.com.servire.api.relatorios.dto.ParticipacaoDtos.*;
import br.com.servire.api.voluntario.FuncaoEscala;
import java.time.LocalDate;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/relatorios/participacao")
public class ParticipacaoController {
  private final ParticipacaoService service;

  public ParticipacaoController(ParticipacaoService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_AUDITORIA')")
  public Pagina listar(
      @RequestParam LocalDate de,
      @RequestParam LocalDate ate,
      @RequestParam(defaultValue = "") String busca,
      @RequestParam(required = false) Presenca presenca,
      @RequestParam(required = false) RespostaParticipacao resposta,
      @RequestParam(required = false) FuncaoEscala funcao,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "30") int tamanho) {
    return service.listar(de, ate, busca, presenca, resposta, funcao, pagina, tamanho);
  }

  @GetMapping("/csv")
  @PreAuthorize("hasAuthority('PERM_AUDITORIA') and hasAuthority('PERM_RELATORIO_EXPORTAR')")
  public ResponseEntity<byte[]> csv(
      @RequestParam LocalDate de,
      @RequestParam LocalDate ate,
      @RequestParam(defaultValue = "") String busca,
      @RequestParam(required = false) Presenca presenca,
      @RequestParam(required = false) RespostaParticipacao resposta,
      @RequestParam(required = false) FuncaoEscala funcao) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=participacao.csv")
        .cacheControl(CacheControl.noStore())
        .body(service.exportar(de, ate, busca, presenca, resposta, funcao));
  }
}
