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
    private final ConsumoHistoricoService historico;
    private final ReconciliadorArmazenamento armazenamento;

    public MinhaContaController(MinhaContaService minhaContaService, CotasService cotas, ReconciliadorArmazenamento armazenamento,ConsumoHistoricoService historico) {
        this.minhaContaService = minhaContaService;
        this.cotas = cotas;this.historico=historico;
        this.armazenamento = armazenamento;
    }

    @GetMapping("/consumo")
    @PreAuthorize("hasAuthority('PERM_PAROQUIA')")
    public CotasService.Consumo consumo() {var d=cotas.consumo();historico.registrar(br.com.servire.api.tenant.TenantContext.get(),d);return d;}

    @GetMapping("/consumo/historico") @PreAuthorize("hasAuthority('PERM_PAROQUIA')")
    public java.util.List<ConsumoHistoricoService.Ponto> historico(){return historico.listar(br.com.servire.api.tenant.TenantContext.get());}

    @PostMapping("/armazenamento/conferir")
    @PreAuthorize("hasAuthority('PERM_PAROQUIA_ALTERAR')")
    public ReconciliadorArmazenamento.Resultado conferirArmazenamento(@RequestParam(defaultValue="0") long inicio) {return armazenamento.conferir(inicio);}

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public String obterDados() {
        return minhaContaService.obterDadosMinhaConta();
    }
}
