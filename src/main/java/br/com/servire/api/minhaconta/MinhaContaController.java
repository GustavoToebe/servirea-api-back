package br.com.servire.api.minhaconta;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/minha-conta")
@PreAuthorize("hasAuthority('PERM_PAROQUIA')")
public class MinhaContaController {

    private final MinhaContaService minhaContaService;
    private final CotasService cotas;
    private final ReconciliadorArmazenamento armazenamento;

    public MinhaContaController(MinhaContaService minhaContaService, CotasService cotas, ReconciliadorArmazenamento armazenamento) {
        this.minhaContaService = minhaContaService;
        this.cotas = cotas;
        this.armazenamento = armazenamento;
    }

    @GetMapping("/consumo")
    @PreAuthorize("hasAuthority('PERM_PAROQUIA')")
    public CotasService.Consumo consumo() {return cotas.consumo();}

    @PostMapping("/armazenamento/conferir")
    @PreAuthorize("hasAuthority('PERM_PAROQUIA_ALTERAR')")
    public ReconciliadorArmazenamento.Resultado conferirArmazenamento(@RequestParam(defaultValue="0") long inicio) {return armazenamento.conferir(inicio);}

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public String obterDados() {
        return minhaContaService.obterDadosMinhaConta();
    }
}
