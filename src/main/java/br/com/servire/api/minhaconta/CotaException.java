package br.com.servire.api.minhaconta;

import br.com.servire.api.web.ApiException;
import org.springframework.http.HttpStatus;

public class CotaException extends ApiException {
    private final String codigo;
    public CotaException(HttpStatus status, String codigo, String mensagem) {
        super(status, mensagem); this.codigo = codigo;
    }
    @Override public String getCodigo() {return codigo;}
}
