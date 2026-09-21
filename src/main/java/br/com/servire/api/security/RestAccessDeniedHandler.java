package br.com.servire.api.security;

import br.com.servire.api.web.ApiError;
import br.com.servire.api.web.ForbiddenException;
import br.com.servire.api.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Usuário autenticado, mas sem permissão para o recurso — equivalente a
 * {@link ForbiddenException}, só que para o caso em que o próprio Spring
 * Security nega o acesso antes de chegar a um controller (ex.: uma
 * restrição por role em {@code authorizeHttpRequests}, hoje não usada mas
 * prevista para módulos de negócio futuros). Mesmo raciocínio de formato
 * de {@link RestAuthenticationEntryPoint} - ver a javadoc de lá.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.name(),
                "Acesso negado.",
                request.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY),
                null);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
