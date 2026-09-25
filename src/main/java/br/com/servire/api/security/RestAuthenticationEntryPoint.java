package br.com.servire.api.security;

import br.com.servire.api.web.ApiError;
import br.com.servire.api.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

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
 *
 * <p><b>Bug real #5 (21/09/2026, ver README.md):</b> esta classe injetava
 * {@code com.fasterxml.jackson.databind.ObjectMapper} (Jackson 2). Isso
 * compilava (a classe existe no classpath, trazida transitivamente pelo
 * jjwt-jackson — ver Bug #4), mas o Spring Boot 4.1.1 nunca registra um
 * bean desse tipo: a partir do Boot 4, o Jackson padrão é a linha 3.x
 * ({@code tools.jackson.*}, grupo Maven {@code tools.jackson.*}), e a
 * auto-configuration registra um bean {@link JsonMapper}
 * ({@code tools.jackson.databind.json.JsonMapper}), não mais um
 * {@code ObjectMapper} do Jackson 2. Por isso o contexto Spring falhava
 * ao subir com {@code NoSuchBeanDefinitionException}. O Jackson 2 continua
 * no projeto — mas só como motor de serialização interno do jjwt-jackson
 * (Bug #4), nunca injetado no código da aplicação.</p>
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    public RestAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.name(),
                "Autenticação necessária, ou token ausente/inválido/expirado.",
                null,
                request.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY),
                null);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(), body);
    }
}
