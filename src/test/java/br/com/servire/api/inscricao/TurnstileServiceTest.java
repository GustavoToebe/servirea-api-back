package br.com.servire.api.inscricao;

import br.com.servire.api.web.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Teste unitário de {@link TurnstileService} (débito técnico da Fase 8,
 * seção 44 do plano mestre, adiado até esta rodada — 22/09/2026).
 *
 * <p>Usa {@link MockRestServiceServer#bindTo(RestClient.Builder)} — o jeito
 * oficial do Spring de testar código baseado em {@link RestClient} sem
 * rede real nem precisar subir o Spring context inteiro (bem mais rápido
 * que um {@code @SpringBootTest}, e evita a dependência de configurar
 * {@code servire.turnstile.secret-key} de verdade só para o teste). Não
 * usa {@code AbstractIntegrationTest}/Testcontainers — esta classe não
 * toca banco.</p>
 */
class TurnstileServiceTest {

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    @Test
    void falhaFechadoQuandoSecretKeyNaoConfigurada() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        TurnstileService service = new TurnstileService(builder, new TurnstileProperties("", VERIFY_URL));

        // Regra inegociável (ver javadoc de TurnstileService): sem
        // secretKey configurada, a inscrição é recusada — nem chega a
        // tentar chamar o Cloudflare.
        assertThatThrownBy(() -> service.validar("qualquer-token", "1.2.3.4"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void falhaFechadoQuandoTokenDoClienteVazio() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        TurnstileService service = new TurnstileService(builder, new TurnstileProperties("segredo-teste", VERIFY_URL));

        assertThatThrownBy(() -> service.validar("", "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.validar(null, "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aceitaQuandoCloudflareRespondeSuccessTrue() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\": true}", MediaType.APPLICATION_JSON));

        TurnstileService service = new TurnstileService(builder, new TurnstileProperties("segredo-teste", VERIFY_URL));

        assertThatCode(() -> service.validar("token-valido", "1.2.3.4")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void recusaQuandoCloudflareRespondeSuccessFalse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\": false, \"error-codes\": [\"invalid-input-response\"]}",
                        MediaType.APPLICATION_JSON));

        TurnstileService service = new TurnstileService(builder, new TurnstileProperties("segredo-teste", VERIFY_URL));

        assertThatThrownBy(() -> service.validar("token-invalido", "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);
        server.verify();
    }

    /**
     * Regra inegociável: falha da infraestrutura própria (Cloudflare fora
     * do ar, 500, timeout) também recusa a inscrição — nunca "deixa passar
     * sem validar" só porque o Turnstile está indisponível.
     */
    @Test
    void falhaFechadoQuandoChamadaHttpFalha() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        TurnstileService service = new TurnstileService(builder, new TurnstileProperties("segredo-teste", VERIFY_URL));

        assertThatThrownBy(() -> service.validar("token-qualquer", "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);
        server.verify();
    }

    @Test
    void naoValidaSeIpRemetenteNuloOuEmBranco() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\": true}", MediaType.APPLICATION_JSON));

        TurnstileService service = new TurnstileService(builder, new TurnstileProperties("segredo-teste", VERIFY_URL));

        // ipRemetente nulo não deve quebrar a montagem do form nem a
        // chamada — só omite o campo opcional "remoteip".
        assertThatCode(() -> service.validar("token-valido", null)).doesNotThrowAnyException();
        server.verify();
    }
}
