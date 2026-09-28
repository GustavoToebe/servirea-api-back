package br.com.servire.api.comunicacao;

import br.com.servire.api.web.ApiException;
import org.springframework.http.HttpStatus;

/** Falha ao falar com o Evolution Go. Mensagem genérica; o detalhe fica só no log. */
public class WhatsappException extends ApiException {

    public WhatsappException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }
}
