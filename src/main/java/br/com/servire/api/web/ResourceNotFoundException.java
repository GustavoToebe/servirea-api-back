package br.com.servire.api.web;

import org.springframework.http.HttpStatus;

/** Recurso pedido não existe (ou, no contexto multi-tenant futuro, não existe PARA o tenant atual — nunca revelar que existe em outro tenant). */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
