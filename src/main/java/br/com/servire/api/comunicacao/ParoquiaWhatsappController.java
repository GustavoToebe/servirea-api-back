package br.com.servire.api.comunicacao;

import br.com.servire.api.comunicacao.dto.TestarWhatsappRequest;
import br.com.servire.api.comunicacao.dto.WhatsappConfigRequest;
import br.com.servire.api.comunicacao.dto.WhatsappConfigResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** WhatsApp (Evolution Go) da paróquia: o token nunca volta na resposta. */
@RestController
@RequestMapping("/tenant/whatsapp")
public class ParoquiaWhatsappController {

    private final ParoquiaWhatsappService service;

    public ParoquiaWhatsappController(ParoquiaWhatsappService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('PERM_PAROQUIA')")
    @GetMapping
    public WhatsappConfigResponse buscar() {
        return service.buscar();
    }

    @PreAuthorize("hasAuthority('PERM_PAROQUIA_ALTERAR')")
    @PutMapping
    public WhatsappConfigResponse salvar(@RequestBody @Valid WhatsappConfigRequest request) {
        return service.salvar(request);
    }

    @PreAuthorize("hasAuthority('PERM_PAROQUIA_ALTERAR')")
    @PostMapping("/testar")
    public ResponseEntity<Void> testar(@RequestBody @Valid TestarWhatsappRequest request) {
        service.testar(request.telefone());
        return ResponseEntity.noContent().build();
    }
}
