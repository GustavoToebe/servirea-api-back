package br.com.servire.api.tenant;

import java.util.UUID;

/**
 * Contexto de tenant da requisição atual (seção 20 do plano mestre).
 * Implementado com {@link ThreadLocal} porque cada requisição HTTP é
 * atendida por sua própria thread no modelo padrão do Spring MVC
 * (Tomcat), então não há necessidade de propagação entre threads nesta
 * fase (isso mudaria se a aplicação passasse a usar threads virtuais com
 * troca de contexto assíncrona, mas isto está fora de escopo aqui).
 *
 * <p><b>Regra crítica (seção 20): nunca deixar o TenantContext
 * persistente</b> entre requisições. Todo código que chama {@link #set}
 * DEVE chamar {@link #clear} num bloco {@code finally}, mesmo em caso de
 * exceção - do contrário, uma thread do pool do Tomcat reaproveitada
 * para uma requisição de OUTRO tenant herdaria o tenant errado, o que
 * seria uma falha de isolamento P0 (seção 78/79/80).</p>
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId não pode ser nulo");
        }
        CURRENT_TENANT.set(tenantId);
    }

    /** @return o tenant da requisição atual, ou {@code null} se nenhum foi definido. */
    public static UUID get() {
        return CURRENT_TENANT.get();
    }

    /**
     * Remove o tenant da thread atual. Deve ser chamado sempre em
     * {@code finally}, ao final de toda requisição que chamou
     * {@link #set} (ver {@code br.com.servire.api.security.JwtAuthenticationFilter}).
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
