package br.com.servire.api.security;

import br.com.servire.api.web.ApiError;
import br.com.servire.api.web.ForbiddenException;
import br.com.servire.api.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;

/**
 * Usuário autenticado, mas sem permissão para o recurso — equivalente a
 * {@link ForbiddenException}, só que para o caso em que o próprio Spring
 * Security nega o acesso antes de chegar a um controller (ex.: uma
 * restrição por role em {@code authorizeHttpRequests}, hoje não usada mas
 * prevista para módulos de negócio futuros). Mesmo raciocínio de formato
 * de {@link RestAuthenticationEntryPoint} - ver a javadoc de lá, inclusive
 * sobre o Bug real #5 (injeção de {@link JsonMapper} do Jackson 3 no lugar
 * do {@code ObjectMapper} do Jackson 2, que o Spring Boot 4 não registra
 * mais como bean).
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public RestAccessDeniedHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.name(),
                "Acesso negado.",
                null,
                request.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY),
                null);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(), body);
    }
}
