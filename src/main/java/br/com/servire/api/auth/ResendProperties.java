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
 * <p>{@code from} tem valor padrão {@code onboarding@resend.dev} — o
 * domínio de teste do próprio Resend, que entrega SÓ para o e-mail dono da
 * conta (suficiente para validar o fluxo ponta a ponta antes da
 * verificação de domínio estar pronta, e o mesmo endereço que o usuário já
 * usou para testar no painel do Resend em 22/09/2026). Quando o domínio
 * {@code servirea.com.br} (seção 122 item 16, registrado em 22/09/2026)
 * estiver VERIFICADO no painel do Resend (passo manual do usuário,
 * apontando o DNS pelo Cloudflare — MX + TXT/SPF + TXT/DKIM, ver
 * README/plano mestre), trocar {@code RESEND_FROM} para um endereço desse
 * domínio (ex. {@code naoresponda@servirea.com.br}) passa a entregar para
 * qualquer destinatário, não só o dono da conta.</p>
 */
@ConfigurationProperties(prefix = "servire.email.resend")
public record ResendProperties(
        String apiKey,
        @DefaultValue("onboarding@resend.dev") String from,
        @DefaultValue("https://api.resend.com/emails") String apiUrl) {
}
