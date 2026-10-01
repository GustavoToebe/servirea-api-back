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

    void enviarConvite(String destinatario, String linkComToken);

    void enviarAvisoAcesso(String destinatario, String nomeParoquia);

    void enviarAlertaIntegracao(String destinatario, String mensagem);

    /**
     * E-mail de comunicado da paróquia (PLANO-005): HTML já renderizado, anexos
     * opcionais e {@code responderPara} (e-mail da paróquia) quando houver.
     */
    void enviarComunicado(String para, String assunto, String html, java.util.List<Anexo> anexos, String responderPara);
    /** Chave persistente da mensagem, reaproveitada em recuperações da fila. */
    default void enviarComunicadoIdempotente(String para, String assunto, String html, java.util.List<Anexo> anexos, String responderPara, String chave) {
        enviarComunicado(para, assunto, html, anexos, responderPara);
    }
}
