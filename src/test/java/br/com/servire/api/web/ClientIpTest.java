package br.com.servire.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpTest {

    @Test
    void ignoraXForwardedForForjadoPeloCliente() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.10");

        assertThat(ClientIp.de(request)).isEqualTo("203.0.113.10");
    }
}
