package br.com.servire.api.web;

import org.springframework.http.HttpStatus;

/** Requisição inválida por uma regra de negócio (não de validação de campo — essa é tratada à parte via {@code @Valid}, ver {@link GlobalExceptionHandler}). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
