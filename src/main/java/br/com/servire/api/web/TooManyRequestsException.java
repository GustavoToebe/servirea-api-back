package br.com.servire.api.web;

import org.springframework.http.HttpStatus;

/**
 * Limite de tentativas excedido — usado pelo rate limit do formulário
 * público de inscrição (Fase 8, seção 45 do plano mestre). HTTP 429, não
 * coberto pelas demais subclasses de {@link ApiException}.
 */
public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
