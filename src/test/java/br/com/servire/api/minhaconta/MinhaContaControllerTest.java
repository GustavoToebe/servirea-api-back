package br.com.servire.api.minhaconta;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.integracao.HmacAssinatura;
import br.com.servire.api.integracao.IntegracaoProperties;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /minha-conta: repasse assinado para a Central e tradução dos erros para a pessoa. */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "servire.integracao.central-url=http://central.test",
        "servire.integracao.chave-saida-id=teste-servire",
        "servire.integracao.chave-saida-segredo=c2VncmVkby1kZS10ZXN0ZS1uYW8tdXNhci1lbS1wcm9kdWNhby0wMTIzNDU2Nzg5"
})
class MinhaContaControllerTest extends AbstractIntegrationTest {

    private static final byte[] SEGREDO = "segredo-de-teste-nao-usar-em-producao-0123456789".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private MinhaContaService minhaContaService;

    @Autowired
    private IntegracaoProperties properties;

    private MockRestServiceServer central;
    private UUID tenantId;

    @BeforeEach
    void preparar() {
        central = MockRestServiceServer.bindTo(restClientBuilder).build();
        ReflectionTestUtils.setField(minhaContaService, "httpBuilder", restClientBuilder);
        tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
    }

    @AfterEach
    void limpar() {
        ReflectionTestUtils.setField(minhaContaService, "properties", properties);
        TenantContext.clear();
    }

    private String caminho() {
        return "/integracao/v1/produtos/SERVIREA/instancias/" + tenantId + "/minha-conta";
    }

    private Authentication usuario(String... permissoes) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(UUID.randomUUID(), tenantId, UsuarioTenant.Role.ADMIN, false), null,
                java.util.Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toList());
    }

    private ResultActions consultar() throws Exception {
        return mvc.perform(get("/minha-conta").with(authentication(usuario("PERM_PAROQUIA"))));
    }

    @Test
    void consultaACentralDaPropriaParoquiaComAssinaturaERepassaAResposta() throws Exception {
        String json = "{\"cliente\":{\"nome\":\"Paróquia Teste\"},\"contratacao\":{\"planoNome\":\"Base\"},\"cobrancas\":[]}";
        central.expect(requestTo("http://central.test" + caminho()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> {
                    String esperada = HmacAssinatura.assinar(SEGREDO, "GET", caminho(),
                            request.getHeaders().getFirst("X-Integracao-Timestamp"),
                            request.getHeaders().getFirst("X-Integracao-Nonce"), new byte[0]);
                    assertThat(request.getHeaders().getFirst("X-Integracao-Assinatura")).isEqualTo(esperada);
                    assertThat(request.getHeaders().getFirst("X-Integracao-Chave")).isEqualTo("teste-servire");
                })
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        consultar().andExpect(status().isOk()).andExpect(content().json(json));
        central.verify();
    }

    @Test
    void instanciaSemContratacaoNaCentralVira404ComMensagem() throws Exception {
        central.expect(requestTo("http://central.test" + caminho())).andRespond(withStatus(HttpStatus.NOT_FOUND));

        consultar().andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTA_NAO_ENCONTRADA"))
                .andExpect(jsonPath("$.message").value("Sua paróquia ainda não tem uma contratação ativa na Central."));
    }

    @Test
    void centralComErroVira502ComMensagemParaAPessoa() throws Exception {
        central.expect(requestTo("http://central.test" + caminho())).andRespond(withServerError());

        consultar().andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("CENTRAL_INDISPONIVEL"))
                .andExpect(jsonPath("$.message").value(MinhaContaService.MENSAGEM_INDISPONIVEL));
    }

    @Test
    void centralForaDoArVira502() throws Exception {
        central.expect(requestTo("http://central.test" + caminho())).andRespond(withException(new IOException("conexão recusada")));

        consultar().andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(MinhaContaService.MENSAGEM_INDISPONIVEL));
    }

    @Test
    void assinaturaRecusadaPelaCentralVira502EnaoErroInterno() throws Exception {
        central.expect(requestTo("http://central.test" + caminho())).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        consultar().andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("CENTRAL_INDISPONIVEL"));
    }

    @Test
    void integracaoSemChaveVira503SemChamarACentral() throws Exception {
        ReflectionTestUtils.setField(minhaContaService, "properties", new IntegracaoProperties(
                properties.chavesEntrada(), "", "", "", properties.toleranciaHoras(), properties.alertaEmail()));
        central.expect(never(), requestTo("http://central.test" + caminho()));

        consultar().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("INTEGRACAO_NAO_CONFIGURADA"));
        central.verify();
    }

    @Test
    void semPermissaoDaParoquiaRecebe403() throws Exception {
        mvc.perform(get("/minha-conta").with(authentication(usuario("PERM_PESSOA"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get("/minha-conta")).andExpect(status().isUnauthorized());
    }
}
