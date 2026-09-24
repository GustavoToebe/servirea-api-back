package br.com.servire.api.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Gera, todo dia às 03:00 (Brasília), as cobranças das assinaturas ativas
 * — sem isso a listagem e o dashboard só enxergariam o atraso de quem teve
 * o financeiro aberto no painel.
 *
 * <p><b>Só gera cobrança. Nunca bloqueia paróquia</b> (decidido com o
 * usuário em 23/09/2026: o bloqueio após 3 dias da seção 131.3 fica
 * manual por enquanto). Desligado no profile {@code test}
 * ({@code servire.billing.job.enabled=false}) para não rodar no meio de um
 * teste.</p>
 */
@Component
@ConditionalOnProperty(prefix = "servire.billing.job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BillingJob {

    private static final Logger log = LoggerFactory.getLogger(BillingJob.class);

    private final BillingService billingService;

    public BillingJob(BillingService billingService) {
        this.billingService = billingService;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    public void gerarCobrancas() {
        int geradas = billingService.gerarCobrancasDeTodas();
        log.info("Job de cobranças: {} cobrança(s) nova(s) gerada(s).", geradas);
    }
}
