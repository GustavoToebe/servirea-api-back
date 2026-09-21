package br.com.servire.api.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void geraUmRequestIdQuandoHeaderNaoVemNaRequisicao() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/qualquer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdDuranteAChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> requestIdDuranteAChain.set(MDC.get(RequestIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        String headerDevolvido = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(headerDevolvido).isNotBlank();
        assertThat(requestIdDuranteAChain.get()).isEqualTo(headerDevolvido);
        // MDC deve estar limpo depois da requisição, nunca vazando para a próxima.
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void propagaOMesmoRequestIdQuandoClienteJaEnviouUm() throws Exception {
        String requestIdDoCliente = "id-vindo-do-cliente-123";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/qualquer");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, requestIdDoCliente);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(requestIdDoCliente);
    }
}
