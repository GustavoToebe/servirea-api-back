package br.com.servire.api.minhaconta;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/minha-conta")
@PreAuthorize("hasAuthority('PERM_PAROQUIA')")
public class MinhaContaController {

    private final MinhaContaService minhaContaService;

    public MinhaContaController(MinhaContaService minhaContaService) {
        this.minhaContaService = minhaContaService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public String obterDados() {
        return minhaContaService.obterDadosMinhaConta();
    }
}
