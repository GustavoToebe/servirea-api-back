package br.com.servire.api.integracao;

import br.com.servire.api.auth.dto.AccessTokenResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/suporte")
public class SuporteTrocaController {

    private final SuporteTrocaService service;

    public SuporteTrocaController(SuporteTrocaService service) {
        this.service = service;
    }

    @PostMapping("/trocar")
    public AccessTokenResponse trocar(@Valid @RequestBody Pedido pedido) {
        return service.trocar(pedido.codigo());
    }

    public record Pedido(@NotBlank String codigo) {
    }
}
