package br.com.servire.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do provedor de e-mail Resend (Fase 11 do plano mestre,
 * seção 32/58) — lida do prefixo {@code servire.email.resend}.
 *
 * <p>{@code apiKey} sem valor padrão de propósito, mesma regra de
 * {@code JWT_SECRET}/{@code SUPABASE_SERVICE_ROLE_KEY}/
 * {@code TURNSTILE_SECRET_KEY} (seção 90: nunca versionar segredo/endpoint
 * de produção) — só existe via variável de ambiente {@code RESEND_API_KEY}.
 *
 * <p>{@code from} tem valor padrão {@code onboarding@resend.dev} aqui — o
 * domínio de teste do próprio Resend, que entrega SÓ para o e-mail dono da
 * conta (suficiente para validar o fluxo ponta a ponta antes da
 * verificação de domínio estar pronta, e o mesmo endereço que o usuário já
 * usou para testar no painel do Resend em 22/09/2026). Esse valor só é
 * usado de fato pelos profiles que não sobrescrevem
 * {@code servire.email.resend.from} (dev/test) — {@code application-prod.yml}
 * já sobrescreve para {@code contato@servirea.com.br} (decidido com o
 * usuário em 22/09/2026).
 *
 * <p><b>Domínio verificado (23/09/2026):</b> {@code servirea.com.br}
 * (seção 122 item 16) está "Verified" no painel do Resend desde 23/09/2026
 * 00:25, e um envio real com {@code contato@servirea.com.br} chegou no
 * Gmail com SPF, DKIM e DMARC em PASS (ver "Próximos passos" no README.md).
 * {@code onboarding@resend.dev} continua como padrão só aqui no record, para
 * dev/test; se o domínio perder a verificação, sobrescrever
 * {@code RESEND_FROM} de volta para ele via variável de ambiente.</p>
 */
@ConfigurationProperties(prefix = "servire.email.resend")
public record ResendProperties(
        String apiKey,
        @DefaultValue("onboarding@resend.dev") String from,
        @DefaultValue("https://api.resend.com/emails") String apiUrl) {
}
