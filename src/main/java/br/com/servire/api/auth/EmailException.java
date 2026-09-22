package br.com.servire.api.auth;

/**
 * Falha de infraestrutura ao falar com o provedor de e-mail — deliberadamente
 * NÃO é uma {@link br.com.servire.api.web.ApiException}: cai no handler
 * genérico de {@code GlobalExceptionHandler} (HTTP 500, mensagem pública
 * neutra, detalhe completo só no log) — mesmo raciocínio de
 * {@code StorageException}.
 */
public class EmailException extends RuntimeException {

    public EmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
