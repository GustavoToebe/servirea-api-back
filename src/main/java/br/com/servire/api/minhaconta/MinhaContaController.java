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
    private final CotasService cotas;

    public MinhaContaController(MinhaContaService minhaContaService, CotasService cotas) {
        this.minhaContaService = minhaContaService;
        this.cotas = cotas;
    }

    @GetMapping("/consumo")
    @PreAuthorize("hasAuthority('PERM_PAROQUIA')")
    public CotasService.Consumo consumo() {return cotas.consumo();}

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public String obterDados() {
        return minhaContaService.obterDadosMinhaConta();
    }
}
