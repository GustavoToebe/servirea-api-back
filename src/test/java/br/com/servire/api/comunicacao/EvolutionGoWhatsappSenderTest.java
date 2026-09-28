package br.com.servire.api.comunicacao;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EvolutionGoWhatsappSenderTest {

    private final WhatsappProperties props = new WhatsappProperties("evolution",
            new WhatsappProperties.Evolution("https://whats.teste/", "/send/text"), 0, 0);

    @Test
    void enviaNoFormatoDoEvolutionGo() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://whats.teste/send/text"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "tok-123"))
                .andExpect(jsonPath("$.number").value("5545999998888"))
                .andExpect(jsonPath("$.text").value("Olá"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        new EvolutionGoWhatsappSender(builder, props).enviarTexto("sao-jose", "tok-123", "(45) 99999-8888", "Olá");
        server.verify();
    }

    @Test
    void respostaDeErroViraWhatsappException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://whats.teste/send/text")).andRespond(withServerError());

        assertThatThrownBy(() -> new EvolutionGoWhatsappSender(builder, props)
                .enviarTexto("sao-jose", "tok-123", "45999998888", "Olá"))
                .isInstanceOf(WhatsappException.class);
    }
}
