package br.com.servire.api.auth;

/**
 * Abstração de envio de e-mail (mesmo espírito da interface
 * {@code FileStorage} planejada na seção 53 do plano mestre — desacoplar
 * a regra de negócio do provedor concreto). Nenhum provedor de e-mail foi
 * decidido ainda com o usuário; ver {@link LoggingEmailSender} para a
 * implementação-placeholder usada por enquanto.
 */
public interface EmailSender {

    void enviarLinkResetSenha(String destinatario, String linkComToken);
}
