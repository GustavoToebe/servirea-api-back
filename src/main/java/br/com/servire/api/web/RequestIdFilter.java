package br.com.servire.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.UUID;

/**
 * Gera (ou propaga) um identificador de requisição, colocado no MDC do
 * SLF4J para aparecer em todo log da requisição (seção 65 do plano
 * mestre — campo "requestId" nos logs JSON estruturados) e devolvido no
 * header de resposta {@code X-Request-Id}, para correlação com o
 * front-end e com ferramentas externas.
 *
 * <p>Os campos "tenantId" e "userId" do formato de log da seção 65 ainda
 * não são preenchidos aqui — só existirão a partir da Fase 4
 * (TenantContext) e Fase 5 (autenticação própria). Até lá, os logs saem
 * com esses campos ausentes, não com um valor inventado.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends GenericFilterBean {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestId = httpRequest.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        httpResponse.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Sempre limpar o MDC ao final — threads (inclusive virtuais, que
            // podem ser reaproveitadas de outras formas pelo runtime) não
            // devem carregar contexto de uma requisição para a próxima.
            MDC.remove(MDC_KEY);
        }
    }
}
