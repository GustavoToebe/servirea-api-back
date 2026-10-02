package br.com.servire.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementação-placeholder de {@link EmailSender}: só loga o link em vez
 * de enviar e-mail de verdade.
 *
 * <p><b>Decisão de escopo (Fase 5, 21/09/2026 — SUPERADA na Fase 11, ver
 * abaixo):</b> nenhum provedor de e-mail tinha sido decidido com o usuário
 * até então — decidir isso não era uma decisão técnica que este projeto
 * devesse tomar sozinho. Em vez de travar o endpoint {@code POST
 * /auth/forgot-password} esperando essa decisão, ou inventar uma
 * integração com um provedor específico sem confirmação, o fluxo inteiro
 * (geração de token, expiração, uso único) foi implementado e testado de
 * verdade — só a "última milha" (o e-mail chegar de fato na caixa de
 * entrada do usuário) ficou como esta implementação-placeholder,
 * substituível depois só trocando o bean sem tocar em
 * {@code PasswordResetTokenService}/{@code AuthController}.</p>
 *
 * <p><b>Fase 11 (22/09/2026):</b> o provedor FOI decidido (Cloudflare + Resend,
 * ver {@link ResendEmailSender}) — esta classe continua existindo como o
 * bean padrão para dev/test ({@code @ConditionalOnProperty(...,
 * matchIfMissing = true)}: ativa quando {@code servire.email.provider} é
 * {@code log} OU está ausente), evitando que rodar a aplicação localmente
 * ou os testes de integração disparem e-mails de verdade sem essa troca
 * explícita de configuração. Em produção, {@code application-prod.yml}
 * define {@code servire.email.provider: resend}, desativando este bean e
 * ativando {@link ResendEmailSender} no lugar.</p>
 *
 * <p>O stub registra somente que uma entrega foi solicitada. Tokens de
 * redefinição/convite não são registrados nem em DEBUG. Usar provedor de
 * teste com captura de e-mail para conferir o link; nunca logs compartilhados.</p>
 */
@Component
@ConditionalOnProperty(prefix = "servire.email", name = "provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void enviarLinkResetSenha(String destinatario, String linkComToken) {
        log.debug("[STUB — sem provedor de e-mail configurado, ver javadoc de LoggingEmailSender] "
                + "Pedido de reset de senha para {} (link omitido)", destinatario);
    }

    @Override
    public void enviarConvite(String destinatario, String linkComToken) {
        log.debug("[STUB] Convite para {} (link omitido)", destinatario);
    }

    @Override
    public void enviarAvisoAcesso(String destinatario, String nomeParoquia) {
        log.debug("[STUB] Acesso concedido a {} na paróquia {}", destinatario, nomeParoquia);
    }

    @Override
    public void enviarAlertaIntegracao(String destinatario, String mensagem) {
        log.debug("[STUB] Alerta de integração para {}: {}", destinatario, mensagem);
    }

    @Override
    public void enviarComunicado(String para, String assunto, String html, java.util.List<Anexo> anexos, String responderPara) {
        log.debug("[STUB] Comunicado para {}: \"{}\" anexos={}", para, assunto,
                anexos == null ? java.util.List.of() : anexos.stream().map(Anexo::nome).toList());
    }
}
