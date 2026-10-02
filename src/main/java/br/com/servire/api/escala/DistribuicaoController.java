package br.com.servire.api.escala;

import br.com.servire.api.escala.dto.DistribuicaoDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Distribuição por regras (F04): prévia sem efeito e aplicação revalidada. */
@RestController
@RequestMapping("/escalas/{id}/distribuicao")
public class DistribuicaoController {
    private final DistribuicaoService service;

    public DistribuicaoController(DistribuicaoService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('PERM_VAGA_DISTRIBUIR')")
    @PostMapping("/previa")
    public Previa previa(@PathVariable UUID id, @Valid @RequestBody PreviaRequest req) {
        return service.previa(id, req.regras());
    }

    @PreAuthorize("hasAuthority('PERM_VAGA_DISTRIBUIR') and hasAuthority('PERM_VAGA_ALOCAR')")
    @PostMapping("/aplicacao")
    public Aplicada aplicar(@PathVariable UUID id, @Valid @RequestBody AplicarRequest req) {
        return service.aplicar(id, req);
    }
}
