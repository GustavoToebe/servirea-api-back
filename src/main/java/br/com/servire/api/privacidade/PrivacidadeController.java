package br.com.servire.api.privacidade;

import br.com.servire.api.privacidade.PrivacidadeDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Privacidade (F09): histórico de consentimentos, exportação autorizada e retenção de comunicados. */
@RestController
public class PrivacidadeController {
    private final ConsentimentoService consentimentos;
    private final PrivacidadeService service;

    public PrivacidadeController(ConsentimentoService consentimentos, PrivacidadeService service) {
        this.consentimentos = consentimentos;
        this.service = service;
    }

    @GetMapping("/pessoas/{id}/consentimentos")
    @PreAuthorize("hasAuthority('PERM_PRIVACIDADE')")
    public PaginaConsentimentos historico(@PathVariable UUID id, @RequestParam(defaultValue = "0") int pagina) {
        return consentimentos.historico(id, pagina);
    }

    @GetMapping("/pessoas/{id}/exportacao")
    @PreAuthorize("hasAuthority('PERM_PRIVACIDADE_EXPORTAR')")
    public ResponseEntity<Exportacao> exportar(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dados-pessoa-" + id + ".json\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.exportar(id));
    }

    @GetMapping("/privacidade/retencao")
    @PreAuthorize("hasAuthority('PERM_PRIVACIDADE')")
    public Retencao retencao() {
        return service.retencao();
    }

    @PutMapping("/privacidade/retencao")
    @PreAuthorize("hasAuthority('PERM_PRIVACIDADE_RETENCAO')")
    public Retencao atualizar(@Valid @RequestBody AtualizarRetencao req) {
        return service.atualizar(req);
    }

    @PostMapping("/privacidade/retencao/execucao")
    @PreAuthorize("hasAuthority('PERM_PRIVACIDADE_RETENCAO')")
    public ResultadoRetencao executar(@Valid @RequestBody ExecutarRetencao req) {
        return service.executar(req);
    }
}
