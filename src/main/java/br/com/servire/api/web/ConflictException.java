package br.com.servire.api.web;

import org.springframework.http.HttpStatus;

/**
 * Estado do recurso conflita com a operação pedida — ex.: aprovar uma
 * inscrição que já não está PENDENTE (seção 20.3), ou controle otimista
 * de versão em escalas (seção 47: HTTP 409 CONFLICT).
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
