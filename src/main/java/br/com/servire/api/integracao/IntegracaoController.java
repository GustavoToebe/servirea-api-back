package br.com.servire.api.integracao;

import br.com.servire.api.integracao.dto.DireitosInstancia;
import br.com.servire.api.integracao.dto.ProvisionarInstanciaRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contrato v1 com a Central. Só entra quem passou pelo HMAC do
 * {@link IntegracaoFiltro}, que concede {@code PERM_INTEGRACAO}.
 */
@RestController
@RequestMapping("/integracao/v1")
@PreAuthorize("hasAuthority('PERM_INTEGRACAO')")
public class IntegracaoController {

    private final IntegracaoInstanciaService service;

    public IntegracaoController(IntegracaoInstanciaService service) {
        this.service = service;
    }

    @PostMapping("/instancias")
    public void provisionar(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                            @Valid @RequestBody ProvisionarInstanciaRequest body,
                            HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        byte[] corpo = request.getInputStream().readAllBytes();
        IntegracaoInstanciaService.ResultadoProvisionamento resultado =
                service.provisionar(idempotencyKey, corpo, body);
        response.setStatus(resultado.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (resultado.replay()) {
            response.setHeader("Idempotency-Replayed", "true");
        }
        response.getWriter().write(resultado.json());
    }

    /** Seção 5.5: o que a Central pode pôr em limites e funcionalidades. */
    @GetMapping("/recursos")
    public List<CatalogoDeRecursos.RecursoDoApp> recursos() {
        return CatalogoDeRecursos.RECURSOS;
    }

    @GetMapping("/instancias/{tenantId}")
    public Map<String, Object> consultar(@PathVariable UUID tenantId) {
        return service.consultar(tenantId);
    }

    @PutMapping("/instancias/{tenantId}/direitos")
    public Map<String, Object> direitos(@PathVariable UUID tenantId, @Valid @RequestBody DireitosInstancia body) {
        return service.aplicarDireitos(tenantId, body);
    }

    @PostMapping("/instancias/{tenantId}/suporte")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> suporte(@PathVariable UUID tenantId, @RequestBody SuportePedido body) {
        var operador = body.operador() == null ? new Operador(null, null, null) : body.operador();
        return service.emitirCodigoSuporte(tenantId, operador.nome(), operador.email(), body.motivo());
    }

    public record SuportePedido(Operador operador, String motivo) {
    }

    public record Operador(UUID id, String nome, String email) {
    }
}
