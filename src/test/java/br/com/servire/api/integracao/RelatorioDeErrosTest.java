package br.com.servire.api.integracao;

import br.com.servirea.comum.seguranca.HmacAssinatura;

import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class RelatorioDeErrosTest {

    private static final byte[] SEGREDO = "segredo-de-teste".getBytes(StandardCharsets.UTF_8);
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-09-26T18:00:00Z"), ZoneOffset.UTC);

    private final JsonMapper json = JsonMapper.builder().build();
    private MockRestServiceServer central;
    private RelatorioDeErros relatorio;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        central = MockRestServiceServer.bindTo(builder).build();
        IntegracaoProperties properties = new IntegracaoProperties(
                null, "teste-servire", Base64.getEncoder().encodeToString(SEGREDO), "http://central.test/", null, null);
        relatorio = new RelatorioDeErros(properties, builder.build(), json, RELOGIO);
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void soErroDeServidorEntraComParoquiaEUsuarioPeloId() {
        UUID tenant = UUID.randomUUID();
        UUID usuario = UUID.randomUUID();
        TenantContext.set(tenant);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(usuario, tenant, UsuarioTenant.Role.ADMIN, false), null, List.of()));

        relatorio.registrar(400, "DADOS_INVALIDOS", "CPF inválido", requisicao());
        relatorio.registrar(503, null, "Storage não configurado.", requisicao());

        assertThat(relatorio.pendentes()).singleElement().satisfies(erro -> {
            assertThat(erro.status()).isEqualTo(503);
            assertThat(erro.tenantId()).isEqualTo(tenant);
            assertThat(erro.usuarioId()).isEqualTo(usuario);
            assertThat(erro.metodo()).isEqualTo("POST");
            assertThat(erro.rota()).isEqualTo("/pessoas");
            assertThat(erro.mensagem()).isEqualTo("Storage não configurado.");
        });
    }

    @Test
    void enviaAssinadoETiraDaFilaQuandoACentralAceita() {
        relatorio.registrar(503, "X", "falhou", requisicao());
        central.expect(requestTo("http://central.test/integracao/v1/produtos/SERVIREA/erros"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    MockClientHttpRequest mock = (MockClientHttpRequest) request;
                    byte[] corpo = mock.getBodyAsBytes();
                    String esperada = HmacAssinatura.assinar(SEGREDO, "POST", RelatorioDeErros.CAMINHO,
                            request.getHeaders().getFirst(IntegracaoFiltro.TIMESTAMP),
                            request.getHeaders().getFirst(IntegracaoFiltro.NONCE), corpo);
                    assertThat(request.getHeaders().getFirst(IntegracaoFiltro.ASSINATURA)).isEqualTo(esperada);
                    assertThat(request.getHeaders().getFirst(IntegracaoFiltro.CHAVE)).isEqualTo("teste-servire");
                    JsonNode erro = json.readTree(corpo).path("erros").get(0);
                    assertThat(erro.path("status").asInt()).isEqualTo(503);
                    assertThat(erro.path("ocorridoEm").asString()).isEqualTo("2026-09-26T18:00:00Z");
                })
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        relatorio.enviar();

        central.verify();
        assertThat(relatorio.pendentes()).isEmpty();
    }

    @Test
    void centralForaDoArMantemOsErrosParaAProximaTentativa() {
        relatorio.registrar(500, null, "Erro inesperado (NullPointerException)", requisicao());
        central.expect(requestTo("http://central.test/integracao/v1/produtos/SERVIREA/erros"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        relatorio.enviar();

        central.verify();
        assertThat(relatorio.pendentes()).hasSize(1);
    }

    @Test
    void filaGuardaNoMaximoOsMaisRecentes() {
        for (int i = 0; i < RelatorioDeErros.MAXIMO + 5; i++) {
            relatorio.registrar(500, null, "erro " + i, requisicao());
        }

        assertThat(relatorio.pendentes()).hasSize(RelatorioDeErros.MAXIMO);
        assertThat(relatorio.pendentes().getFirst().mensagem()).isEqualTo("erro 5");
    }

    private static MockHttpServletRequest requisicao() {
        return new MockHttpServletRequest("POST", "/pessoas");
    }
}
