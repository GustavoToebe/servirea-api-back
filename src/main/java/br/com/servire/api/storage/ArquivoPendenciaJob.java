package br.com.servire.api.storage;

import br.com.servire.api.integracao.AcessoParoquia;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Resolve as pendências de arquivo de cada paróquia (T13): remoções que falharam e uploads sem referência confirmada.
 * Rodar mais de uma vez é seguro (reserva por linha) e repetir a mesma remoção não tem efeito. Desligável por configuração.
 */
@Component
public class ArquivoPendenciaJob {
    private static final Logger log = LoggerFactory.getLogger(ArquivoPendenciaJob.class);

    private final TenantRepository tenants;
    private final AcessoParoquia acesso;
    private final ArquivoCicloService ciclo;
    private final boolean ativo;

    public ArquivoPendenciaJob(TenantRepository tenants, AcessoParoquia acesso, ArquivoCicloService ciclo,
                               @Value("${servire.storage.pendencias-ativo:true}") boolean ativo) {
        this.tenants = tenants;
        this.acesso = acesso;
        this.ciclo = ciclo;
        this.ativo = ativo;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    void agendado() {
        if (ativo) {
            processarAgora();
        }
    }

    public int processarAgora() {
        int total = 0;
        for (Tenant tenant : tenants.findAll()) {
            try {
                if (!acesso.liberada(tenant)) {
                    continue;
                }
                TenantContext.set(tenant.getId());
                try {
                    total += ciclo.processarLote();
                } finally {
                    TenantContext.clear();
                }
            } catch (RuntimeException e) {
                log.error("Pendências de arquivo falharam na paróquia {}: {}", tenant.getId(), e.getClass().getSimpleName());
            }
        }
        return total;
    }
}
