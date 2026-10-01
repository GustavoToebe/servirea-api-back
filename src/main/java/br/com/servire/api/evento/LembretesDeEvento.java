package br.com.servire.api.evento;

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
 * Põe na fila os lembretes de evento, de hora em hora das 9h às 20h (Brasília). Rodar mais de uma vez é
 * seguro (cada inscrição recebe um lembrete só) e recupera o horário perdido se a API estava fora do ar.
 */
@Component
public class LembretesDeEvento {

    private static final Logger log = LoggerFactory.getLogger(LembretesDeEvento.class);

    private final TenantRepository tenants;
    private final AcessoParoquia acessoParoquia;
    private final EventoService eventos;
    private final boolean ativo;

    public LembretesDeEvento(TenantRepository tenants, AcessoParoquia acessoParoquia, EventoService eventos,
                             @Value("${servire.comunicado.fila-ativa:true}") boolean ativo) {
        this.tenants = tenants;
        this.acessoParoquia = acessoParoquia;
        this.eventos = eventos;
        this.ativo = ativo;
    }

    @Scheduled(cron = "0 0 9-20 * * *", zone = "America/Sao_Paulo")
    void agendado() {
        if (ativo) enviarAgora();
    }

    public void enviarAgora() {
        for (Tenant tenant : tenants.findAll()) {
            try {
                if (!acessoParoquia.liberada(tenant)) continue;
                TenantContext.set(tenant.getId());
                try {
                    int n = eventos.enviarLembretes();
                    if (n > 0) log.info("Lembretes de evento na fila: {} (paróquia {})", n, tenant.getId());
                } finally {
                    TenantContext.clear();
                }
            } catch (RuntimeException e) {
                log.error("Lembretes de evento falharam na paróquia {}", tenant.getId(), e);
            }
        }
    }
}
