package br.com.servire.api.notificacao;

import br.com.servire.api.comunicacao.TipoEnvio;
import br.com.servire.api.notificacao.NotificacaoDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Centro de entregas e gatilhos de notificação (F05/F13). */
@RestController
public class NotificacaoController {
    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) {
        this.service = service;
    }

    @GetMapping("/notificacoes/configuracoes")
    @PreAuthorize("hasAuthority('PERM_NOTIFICACAO')")
    public List<Config> configuracoes() {
        return service.configuracoes();
    }

    @PutMapping("/notificacoes/configuracoes/{origem}/{canal}")
    @PreAuthorize("hasAuthority('PERM_NOTIFICACAO_CONFIGURAR')")
    public Config configurar(@PathVariable OrigemNotificacao origem, @PathVariable TipoEnvio canal, @Valid @RequestBody Configurar req) {
        return service.configurar(origem, canal, req);
    }

    @GetMapping("/notificacoes/entregas")
    @PreAuthorize("hasAuthority('PERM_NOTIFICACAO')")
    public Pagina entregas(@RequestParam(required = false) OrigemNotificacao origem, @RequestParam(defaultValue = "0") int pagina) {
        return service.entregas(origem, pagina);
    }

    @PostMapping("/escalas/{id}/notificacoes")
    @PreAuthorize("hasAuthority('PERM_NOTIFICACAO_ENVIAR')")
    public Resultado notificarEscala(@PathVariable UUID id, @Valid @RequestBody EscalaRequest req) {
        return service.notificarEscala(id, req.canal());
    }

    @PostMapping("/mural/avisos/{id}/notificacoes")
    @PreAuthorize("hasAuthority('PERM_NOTIFICACAO_ENVIAR')")
    public Resultado notificarAviso(@PathVariable UUID id, @Valid @RequestBody AvisoRequest req) {
        return service.notificarAviso(id, req.canal(), req.versao());
    }
}
