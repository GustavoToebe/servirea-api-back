package br.com.servire.api.auth;
import br.com.servire.api.web.ApiException;
import org.springframework.http.HttpStatus;
public class MfaException extends ApiException {
    private final String codigo;
    public MfaException(HttpStatus status, String mensagem, String codigo) {super(status, mensagem);this.codigo=codigo;}
    @Override public String getCodigo() {return codigo;}
    static MfaException invalido() {return new MfaException(HttpStatus.UNAUTHORIZED,"Código de autenticação inválido ou já utilizado.","MFA_INVALIDO");}
}
