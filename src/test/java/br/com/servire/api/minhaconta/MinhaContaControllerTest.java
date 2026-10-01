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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = {
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
    private MinhaContaController minhaContaController;

    @Autowired
    private MinhaContaService minhaContaService;

    private MockRestServiceServer central;
    private UUID tenantId;

    @BeforeEach
    void preparar() {
        central = MockRestServiceServer.bindTo(restClientBuilder).build();
        org.springframework.test.util.ReflectionTestUtils.setField(minhaContaService, "httpBuilder", restClientBuilder);
        tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(UUID.randomUUID(), tenantId, UsuarioTenant.Role.ADMIN, false), null, List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_PAROQUIA")
                )));
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void consultaCentralComAssinaturaEPropagaResposta() throws Exception {
        String jsonResposta = "{\"cliente\": {\"nome\": \"Paróquia Teste\"}}";

        central.expect(requestTo("http://central.test/integracao/v1/produtos/SERVIREA/instancias/" + tenantId + "/minha-conta"))
                .andRespond(withSuccess(jsonResposta, MediaType.APPLICATION_JSON));

        mvc.perform(get("/minha-conta")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(
                        SecurityContextHolder.getContext().getAuthentication()
                )))
                .andExpect(status().isOk())
                .andExpect(content().json(jsonResposta));
    }

    @Test
    void retorna502QuandoCentralForaDoAr() throws Exception {
        central.expect(requestTo("http://central.test/integracao/v1/produtos/SERVIREA/instancias/" + tenantId + "/minha-conta"))
                .andRespond(withServerError());

        mvc.perform(get("/minha-conta")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(
                        SecurityContextHolder.getContext().getAuthentication()
                )))
                .andExpect(status().isBadGateway());
    }

    @Test
    void retorna404QuandoInstanciaNaoProvisionada() throws Exception {
        central.expect(requestTo("http://central.test/integracao/v1/produtos/SERVIREA/instancias/" + tenantId + "/minha-conta"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        mvc.perform(get("/minha-conta")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(
                        SecurityContextHolder.getContext().getAuthentication()
                )))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna403ParaUsuarioSemPermissao() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(UUID.randomUUID(), tenantId, UsuarioTenant.Role.ADMIN, false), null, List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("OUTRA_PERMISSAO")
                )));

        mvc.perform(get("/minha-conta")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(
                        SecurityContextHolder.getContext().getAuthentication()
                )))
                .andExpect(status().isForbidden());
    }
}
