package br.com.servire.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação-placeholder de {@link EmailSender}: só loga o link em vez
 * de enviar e-mail de verdade.
 *
 * <p><b>Decisão de escopo (Fase 5, 21/09/2026):</b> nenhum provedor de
 * e-mail (SES, SendGrid, SMTP de terceiro etc.) foi decidido com o usuário
 * até agora — decidir isso não é uma decisão técnica que este projeto deva
 * tomar sozinho. Em vez de travar o endpoint {@code POST
 * /auth/forgot-password} esperando essa decisão, ou inventar uma
 * integração com um provedor específico sem confirmação, o fluxo inteiro
 * (geração de token, expiração, uso único) foi implementado e testado de
 * verdade — só a "última milha" (o e-mail chegar de fato na caixa de
 * entrada do usuário) fica como esta implementação-placeholder,
 * substituível depois só trocando o bean sem tocar em
 * {@code PasswordResetTokenService}/{@code AuthController}.</p>
 *
 * <p><b>Cuidado ao usar em qualquer ambiente compartilhado:</b> o link
 * (que contém o token de reset em texto puro) só é logado em nível DEBUG
 * — nunca em INFO — porque a seção 58 do plano mestre proíbe logar token
 * em log de aplicação. Mesmo em DEBUG, isto não deve rodar em produção com
 * usuários reais antes de existir um {@link EmailSender} de verdade: até
 * lá, qualquer pessoa com acesso ao log do servidor consegue redefinir a
 * senha de qualquer usuário.</p>
 */
@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void enviarLinkResetSenha(String destinatario, String linkComToken) {
        log.debug("[STUB — sem provedor de e-mail configurado, ver javadoc de LoggingEmailSender] "
                + "Link de reset de senha para {}: {}", destinatario, linkComToken);
    }
}
