package br.com.servire.api.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;

/**
 * Tratamento de exceções centralizado (seção 102 do plano mestre — "exception
 * handling" é um dos itens obrigatórios da fundação da Fase 2).
 *
 * <p>Regra geral: exceções de negócio ({@link ApiException} e subclasses)
 * viram respostas previsíveis com a mensagem que a própria exceção definiu
 * (mensagens pensadas para ir ao cliente). Qualquer outra exceção
 * (bug, falha de infraestrutura, exceção do driver JDBC etc.) vira sempre
 * um 500 genérico para o cliente — o detalhe completo (com stack trace) só
 * vai para o log do servidor, nunca para a resposta HTTP. Isso segue a
 * mesma cautela já usada no login atual do Angular (que hoje mostra
 * {@code error.message} cru do Supabase — risco #18 do levantamento
 * original) só que fazendo o oposto de propósito: não repetir esse erro
 * no backend novo.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        log.warn("Erro de negócio tratado: status={} message={}", ex.getStatus(), ex.getMessage());
        return build(ex.getStatus(), ex.getMessage(), request, null);
    }

    /**
     * Rede de segurança do controle otimista de {@code Escala} (Fase 9,
     * seção 47) — {@code EscalaService.atualizar} já faz uma checagem
     * explícita de versão antes de mudar qualquer coisa (path normal),
     * mas se duas requisições concorrentes passarem por aquela checagem
     * quase ao mesmo tempo, é o próprio {@code @Version} do Hibernate que
     * detecta o conflito no flush, lançando esta exceção (o Spring Data
     * já embrulha o {@code OptimisticLockException} nativo do Hibernate
     * nesta). Mesma mensagem de negócio dos dois caminhos, para o cliente
     * nunca ver diferença.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Conflito de edição concorrente (controle otimista): {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "A escala foi alterada por outro usuário. Atualize a página.", request, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Requisição inválida.", request, fieldErrors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Aqui SIM logamos com stack trace completo — é o único lugar que
        // deveria ver o detalhe real do erro. Nunca devolver ex.getMessage()
        // ao cliente neste handler: pode ser uma mensagem de driver JDBC
        // revelando nome de tabela/coluna, ou outro detalhe interno.
        log.error("Erro não tratado ao processar requisição", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro inesperado. Tente novamente ou contate o suporte informando o requestId.",
                request, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        HttpServletRequest httpRequest = ((org.springframework.web.context.request.ServletWebRequest) request)
                .getRequest();
        ApiError body = buildBody(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.", httpRequest, fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpServletRequest httpRequest = ((org.springframework.web.context.request.ServletWebRequest) request)
                .getRequest();
        ApiError body = buildBody(HttpStatus.BAD_REQUEST, "Corpo da requisição malformado ou ilegível.", httpRequest, null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private ApiError.FieldError toFieldError(FieldError fe) {
        return new ApiError.FieldError(fe.getField(), fe.getDefaultMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
                                            List<ApiError.FieldError> fieldErrors) {
        return ResponseEntity.status(status).body(buildBody(status, message, request, fieldErrors));
    }

    private ApiError buildBody(HttpStatus status, String message, HttpServletRequest request,
                                List<ApiError.FieldError> fieldErrors) {
        return new ApiError(
                Instant.now(),
                status.value(),
                status.name(),
                message,
                request != null ? request.getRequestURI() : null,
                MDC.get(br.com.servire.api.web.RequestIdFilter.MDC_KEY),
                fieldErrors
        );
    }
}
