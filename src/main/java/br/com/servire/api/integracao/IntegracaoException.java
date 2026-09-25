package br.com.servire.api.integracao;

import br.com.servire.api.web.ApiException;
import org.springframework.http.HttpStatus;

/** Erro do contrato de integração, com {@code codigo} estável. */
public class IntegracaoException extends ApiException {

    private final String codigo;

    public IntegracaoException(HttpStatus status, String codigo, String message) {
        super(status, message);
        this.codigo = codigo;
    }

    @Override
    public String getCodigo() {
        return codigo;
    }
}
