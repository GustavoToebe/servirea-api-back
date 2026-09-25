package br.com.servire.api.web;

import java.time.Instant;
import java.util.List;

/**
 * Formato único de corpo de erro para toda a API. Mantido enxuto de
 * propósito — nunca inclui stack trace, mensagem crua de exceção interna
 * (JDBC, Hibernate) ou qualquer dado que possa vazar estrutura do banco
 * (seção 65 do plano mestre: nunca logar/expor payload sensível
 * completo).
 *
 * @param timestamp momento da resposta
 * @param status código HTTP numérico (redundante com o header, mas conveniente pro cliente)
 * @param error   nome da constante {@link org.springframework.http.HttpStatus} (ex.: "NOT_FOUND")
 * @param message mensagem segura para exibir ao usuário final
 * @param path    path da requisição que gerou o erro
 * @param requestId o mesmo valor devolvido no header {@code X-Request-Id} — usar para
 *                  correlacionar com os logs do servidor ao investigar um erro reportado
 * @param fieldErrors preenchido apenas em erros de validação (400 por {@code @Valid})
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String codigo,
        String path,
        String requestId,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }
}
