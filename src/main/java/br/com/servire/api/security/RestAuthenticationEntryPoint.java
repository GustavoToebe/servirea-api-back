package br.com.servire.api.security;

import br.com.servire.api.web.ApiError;
import br.com.servire.api.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Ponto de entrada para requisições não autenticadas (sem token, token
 * inválido, ou reprovadas no Kill Switch por {@link JwtAuthenticationFilter})
 * que tentam acessar um endpoint protegido.
 *
 * <p>Roda dentro da cadeia de filtros do Spring Security, fora do alcance
 * do {@code GlobalExceptionHandler} (que só trata exceções lançadas
 * dentro do {@code DispatcherServlet}/controllers) — por isso monta o
 * corpo {@link ApiError} manualmente aqui, mantendo o mesmo formato de
 * erro usado no resto da API em vez da página HTML padrão do
 * container/Spring Security.</p>
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.name(),
                "Autenticação necessária, ou token ausente/inválido/expirado.",
                request.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY),
                null);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
