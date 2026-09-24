package br.com.servire.api.billing;

import br.com.servire.api.billing.dto.CriarAssinaturaRequest;
import br.com.servire.api.billing.dto.FinanceiroResponse;
import br.com.servire.api.billing.dto.GerarCobrancasRequest;
import br.com.servire.api.billing.dto.IsentarCobrancaRequest;
import br.com.servire.api.billing.dto.RegistrarPagamentoRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Financeiro de uma paróquia no painel (V029). Toda escrita devolve o
 * financeiro atualizado, para a tela redesenhar sem outro GET. O
 * {@code id} do path é a paróquia; cobrança de outra paróquia dá 404.
 */
@RestController
@RequestMapping("/admin/paroquias/{id}")
@PreAuthorize("hasAuthority('PERM_BACKOFFICE')")
public class FinanceiroParoquiaController {

    private final BillingService billingService;

    public FinanceiroParoquiaController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/financeiro")
    public FinanceiroResponse financeiro(@PathVariable UUID id) {
        return billingService.financeiro(id);
    }

    @PostMapping("/assinatura")
    public FinanceiroResponse criarAssinatura(@PathVariable UUID id,
                                              @RequestBody @Valid CriarAssinaturaRequest request) {
        return billingService.criarAssinatura(id, request);
    }

    @PostMapping("/assinatura/cancelar")
    public FinanceiroResponse cancelarAssinatura(@PathVariable UUID id) {
        return billingService.cancelarAssinatura(id);
    }

    @PostMapping("/cobrancas/gerar")
    public FinanceiroResponse gerarAdiantadas(@PathVariable UUID id,
                                              @RequestBody @Valid GerarCobrancasRequest request) {
        return billingService.gerarAdiantadas(id, request.ate());
    }

    @PostMapping("/pagamentos")
    public FinanceiroResponse registrarPagamento(@PathVariable UUID id,
                                                 @RequestBody @Valid RegistrarPagamentoRequest request) {
        return billingService.registrarPagamento(id, request);
    }

    @PostMapping("/cobrancas/{cobrancaId}/estornar")
    public FinanceiroResponse estornar(@PathVariable UUID id, @PathVariable UUID cobrancaId) {
        return billingService.estornar(id, cobrancaId);
    }

    @PostMapping("/cobrancas/{cobrancaId}/isentar")
    public FinanceiroResponse isentar(@PathVariable UUID id, @PathVariable UUID cobrancaId,
                                      @RequestBody(required = false) @Valid IsentarCobrancaRequest request) {
        return billingService.isentar(id, cobrancaId, request == null ? null : request.motivo());
    }
}
