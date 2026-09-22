package br.com.servire.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

/**
 * Teste unitário de {@link ResendEmailSender} (Fase 11 do plano mestre,
 * seção 32/58) — débito de teste pago numa rodada posterior à
 * implementação (22/09/2026 à tarde), depois que o segundo
 * {@code mvn clean verify} da Fase 11 confirmou {@code BUILD SUCCESS} sem
 * este bean sequer ser instanciado (o profile de teste força
 * {@code servire.email.provider: log}, ver {@code application-test.yml}).
 *
 * <p>Mesmo padrão de {@code TurnstileServiceTest}:
 * {@link MockRestServiceServer#bindTo(RestClient.Builder)} — testa o
 * formato real da chamada HTTP (endpoint, header, corpo JSON) sem rede
 * real nem precisar subir o Spring context inteiro. Continua sem
 * confirmação contra a API de verdade do Resend (nenhum acesso de rede a
 * serviços externos a partir deste ambiente de pesquisa) — ver "Próximos
 * passos" do README.</p>
 */
class ResendEmailSenderTest {

    private static final String API_URL = "https://api.resend.com/emails";

    @Test
    void falhaAltoECedoQuandoApiKeyNaoConfigurada() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        ResendEmailSender sender = new ResendEmailSender(builder, new ResendProperties("", "onboarding@resend.dev", API_URL));

        assertThatThrownBy(() -> sender.enviarLinkResetSenha("destino@teste.com", "https://app/reset?token=abc"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void falhaAltoECedoQuandoFromNaoConfigurado() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        ResendEmailSender sender = new ResendEmailSender(builder, new ResendProperties("re_chave_teste", "", API_URL));

        assertThatThrownBy(() -> sender.enviarLinkResetSenha("destino@teste.com", "https://app/reset?token=abc"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Confirma o formato de request documentado no javadoc de
     * {@link ResendEmailSender} — o mesmo endpoint/corpo copiado do
     * exemplo do painel do Resend que o usuário compartilhou.
     */
    @Test
    void enviaComSucessoNoFormatoDocumentadoDoResend() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_chave_teste"))
                .andExpect(jsonPath("$.from").value("onboarding@resend.dev"))
                .andExpect(jsonPath("$.to").value("destino@teste.com"))
                .andExpect(jsonPath("$.subject").value("Redefinição de senha — Servire"))
                .andExpect(jsonPath("$.html").value(containsString("https://app/reset?token=abc")))
                .andRespond(withSuccess("{\"id\": \"resend-id-qualquer\"}", MediaType.APPLICATION_JSON));

        ResendEmailSender sender = new ResendEmailSender(
                builder, new ResendProperties("re_chave_teste", "onboarding@resend.dev", API_URL));

        assertThatCode(() -> sender.enviarLinkResetSenha("destino@teste.com", "https://app/reset?token=abc"))
                .doesNotThrowAnyException();
        server.verify();
    }

    /**
     * Regra inegociável (ver javadoc de {@link ResendEmailSender}): enviar
     * é a própria função deste método, então uma falha de infraestrutura
     * (Resend fora do ar, API key errada, domínio de "from" não verificado
     * etc.) precisa propagar como {@link EmailException} — nunca ficar
     * "engolida" feito {@code StorageService.excluir}.
     */
    @Test
    void falhaDeInfraestruturaLancaEmailException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        ResendEmailSender sender = new ResendEmailSender(
                builder, new ResendProperties("re_chave_teste", "onboarding@resend.dev", API_URL));

        assertThatThrownBy(() -> sender.enviarLinkResetSenha("destino@teste.com", "https://app/reset?token=abc"))
                .isInstanceOf(EmailException.class)
                .hasCauseInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void apiKeyInvalidaLancaEmailException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withUnauthorizedRequest());

        ResendEmailSender sender = new ResendEmailSender(
                builder, new ResendProperties("re_chave_invalida", "onboarding@resend.dev", API_URL));

        assertThatThrownBy(() -> sender.enviarLinkResetSenha("destino@teste.com", "https://app/reset?token=abc"))
                .isInstanceOf(EmailException.class);
        server.verify();
    }
}
