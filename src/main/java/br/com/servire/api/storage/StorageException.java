package br.com.servire.api.storage;

/**
 * Falha de infraestrutura ao falar com o Supabase Storage — deliberadamente
 * NÃO é uma {@link br.com.servire.api.web.ApiException}: cai no handler
 * genérico de {@code GlobalExceptionHandler} (HTTP 500, mensagem pública
 * neutra, detalhe completo só no log), porque o cliente não pode fazer
 * nada com o motivo exato (diferente de um {@code BadRequestException} de
 * validação, que é acionável pelo próprio usuário).
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
