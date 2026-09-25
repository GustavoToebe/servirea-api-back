package br.com.servire.api.web;

import org.springframework.http.HttpStatus;

/**
 * Base para exceções de negócio que devem virar uma resposta HTTP
 * previsível (ver {@link GlobalExceptionHandler}). Módulos de negócio
 * futuros (voluntário, inscrição, escala) devem lançar subclasses desta
 * exceção em vez de propagar exceções genéricas ou de acesso a dados —
 * assim o handler global sabe qual status HTTP e qual mensagem pública
 * usar, sem vazar detalhe interno (stack trace, mensagem do driver JDBC
 * etc.) para o cliente.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    protected ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Código estável para a máquina. Nulo no restante da API. */
    public String getCodigo() {
        return null;
    }
}
