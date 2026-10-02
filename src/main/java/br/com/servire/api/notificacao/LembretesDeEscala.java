package br.com.servire.api.notificacao;

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
 * Põe na fila os lembretes de escala, de hora em hora das 8h às 20h (Brasília), só nas paróquias que ligaram o gatilho
 * ESCALA_LEMBRETE por canal (desligado por padrão). Rodar mais de uma vez é seguro: cada celebração é lembrada uma vez por canal.
 */
@Component
public class LembretesDeEscala {
    private static final Logger log = LoggerFactory.getLogger(LembretesDeEscala.class);

    private final TenantRepository tenants;
    private final AcessoParoquia acesso;
    private final NotificacaoService notificacoes;
    private final boolean ativo;

    public LembretesDeEscala(TenantRepository tenants, AcessoParoquia acesso, NotificacaoService notificacoes,
                             @Value("${servire.notificacoes.lembretes-ativo:true}") boolean ativo) {
        this.tenants = tenants;
        this.acesso = acesso;
        this.notificacoes = notificacoes;
        this.ativo = ativo;
    }

    @Scheduled(cron = "0 20 8-20 * * *", zone = "America/Sao_Paulo")
    void agendado() {
        if (ativo) {
            enviarAgora();
        }
    }

    public int enviarAgora() {
        int total = 0;
        for (Tenant tenant : tenants.findAll()) {
            try {
                if (!acesso.liberada(tenant)) {
                    continue;
                }
                TenantContext.set(tenant.getId());
                try {
                    total += notificacoes.lembretesDeEscala();
                } finally {
                    TenantContext.clear();
                }
            } catch (RuntimeException e) {
                log.error("Lembretes de escala falharam na paróquia {}: {}", tenant.getId(), e.getClass().getSimpleName());
            }
        }
        return total;
    }
}
