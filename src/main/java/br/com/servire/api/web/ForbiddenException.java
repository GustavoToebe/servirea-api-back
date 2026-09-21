package br.com.servire.api.web;

import org.springframework.http.HttpStatus;

/**
 * Usuário autenticado, mas sem permissão para a operação — distinto de
 * {@link ResourceNotFoundException}: usar 404 (não 403) sempre que a
 * simples existência do recurso já vazaria informação sobre outro tenant
 * (regra P0 de isolamento, seção 78/79 do plano mestre). Reservar 403
 * para os casos em dentro do MESMO tenant, onde a role/permissão do
 * usuário é insuficiente.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
