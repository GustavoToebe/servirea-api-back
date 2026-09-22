package br.com.servire.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Implementação de {@link EmailSender} chamando diretamente a API REST do
 * Resend via {@link RestClient} (Fase 11 do plano mestre — provedor de
 * e-mail decidido com o usuário em 22/09/2026: Cloudflare como DNS +
 * Resend para disparo, "Combinação Ideal (Custo R$ 0,00)"). Mesmo padrão
 * já usado em {@code SupabaseStorageService}/{@code TurnstileService}: uma
 * chamada HTTP direta, sem SDK dedicado (o SDK oficial {@code resend-java}
 * foi cogitado pelo usuário, mas descartado de propósito — só precisamos
 * de UMA operação simples, mesmo raciocínio da seção 15 do plano mestre
 * sobre manter a stack enxuta).
 *
 * <p><b>Ativação condicional:</b> este bean só existe quando
 * {@code servire.email.provider=resend} (ver
 * {@code application-prod.yml}) — em dev/test, {@link LoggingEmailSender}
 * continua sendo o único bean de {@link EmailSender} por padrão
 * ({@code matchIfMissing = true} na condição dela), então nenhum e-mail
 * real é disparado sem essa troca explícita de configuração.</p>
 *
 * <p><b>Endpoint (documentação pública do Resend, consultada em
 * 22/09/2026 — confirmada contra o próprio código de exemplo Java que o
 * usuário copiou do painel do Resend):</b> {@code POST
 * https://api.resend.com/emails}, header {@code Authorization: Bearer
 * <api-key>}, corpo JSON {@code {"from", "to", "subject", "html"}}. Não
 * foi possível confirmar contra uma chamada real de dentro deste ambiente
 * de pesquisa (sem acesso de rede ao Resend aqui) — <b>ainda não
 * confirmado por um {@code mvn clean verify}/teste manual real</b>; se o
 * próximo envio de verdade (via {@code POST /auth/forgot-password} com
 * {@code EMAIL_PROVIDER=resend}) acusar erro de formato de request/
 * resposta, este é o primeiro lugar a checar.</p>
 *
 * <p><b>Domínio de remetente ({@code from}):</b> até o domínio
 * {@code servirea.com.br} (seção 122 item 16, registrado em 22/09/2026)
 * estar VERIFICADO no painel do Resend (passo manual do usuário, apontando
 * o DNS pelo Cloudflare — MX + TXT/SPF + TXT/DKIM, ver README/plano
 * mestre), {@code servire.email.resend.from} precisa continuar apontando
 * para {@code onboarding@resend.dev} (domínio de teste do próprio Resend,
 * que só entrega para o e-mail dono da conta) — nunca para um endereço
 * {@code @servirea.com.br} ainda não verificado (o Resend recusaria o
 * envio).</p>
 */
@Service
@ConditionalOnProperty(prefix = "servire.email", name = "provider", havingValue = "resend")
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final RestClient restClient;
    private final ResendProperties properties;

    public ResendEmailSender(RestClient.Builder restClientBuilder, ResendProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public void enviarLinkResetSenha(String destinatario, String linkComToken) {
        requireConfigurado();
        Map<String, Object> corpo = Map.of(
                "from", properties.from(),
                "to", destinatario,
                "subject", "Redefinição de senha — Servire",
                "html", corpoHtml(linkComToken));
        try {
            restClient.post()
                    .uri(properties.apiUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Falha de infraestrutura (Resend fora do ar, API key errada,
            // domínio de "from" não verificado etc.) — nunca detalhe do
            // driver HTTP pro cliente, só log (mesma regra de
            // GlobalExceptionHandler para exceção não mapeada). Diferente
            // de SupabaseStorageService.excluir (best-effort, engole a
            // falha): aqui ENVIAR é a própria função deste método, então a
            // falha precisa propagar — nunca ficar sabendo, ao ver a tela
            // de "e-mail enviado", que na verdade nenhum e-mail saiu.
            log.error("Falha ao enviar e-mail via Resend para {}", destinatario, e);
            throw new EmailException("Não foi possível enviar o e-mail de redefinição de senha no momento.", e);
        }
    }

    private String corpoHtml(String linkComToken) {
        return "<p>Você pediu para redefinir sua senha no Servire.</p>"
                + "<p><a href=\"" + linkComToken + "\">Clique aqui para escolher uma nova senha</a>.</p>"
                + "<p>Se você não pediu isso, pode ignorar este e-mail.</p>";
    }

    /**
     * Falha alto e cedo (não um 500 genérico depois de tentar a chamada
     * HTTP) se {@code apiKey}/{@code from} não foram configurados — mesma
     * filosofia de {@code SupabaseStorageService.requireConfigurado}.
     */
    private void requireConfigurado() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()
                || properties.from() == null || properties.from().isBlank()) {
            throw new IllegalStateException(
                    "Provedor de e-mail Resend selecionado (servire.email.provider=resend) mas "
                            + "servire.email.resend.api-key/from ausentes — defina RESEND_API_KEY/RESEND_FROM.");
        }
    }
}
