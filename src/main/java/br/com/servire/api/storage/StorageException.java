package br.com.servire.api.storage;

import br.com.servire.api.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Falha de infraestrutura ao falar com o Supabase Storage, ou Storage
 * sem {@code SUPABASE_URL}/{@code SUPABASE_SERVICE_ROLE_KEY} no processo.
 * Vira HTTP 503 (não 500 genérico) para o operador distinguir "não
 * configurado" / "Supabase recusou" de um bug interno — a mensagem é
 * acionável e não vaza corpo HTTP nem a service role key.
 */
public class StorageException extends ApiException {

    public StorageException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
