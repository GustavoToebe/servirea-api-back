package br.com.servire.api.integracao;

import br.com.servirea.comum.seguranca.HmacAssinatura;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.acesso.PerfilRepository;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.security.JwtService;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato v1 pelo HTTP, com requisições assinadas de verdade (chave
 * {@code teste-central} do {@code application-test.yml}, mesmo segredo dos
 * vetores do contrato). Criado em 25/09/2026, junto com a trava
 * {@code PERM_INTEGRACAO}: o provisionamento, a repetição e as recusas do
 * HMAC não tinham teste.
 */
@AutoConfigureMockMvc
class IntegracaoHttpIntegrationTest extends AbstractIntegrationTest {

    private static final String CHAVE = "teste-central";
    private static final byte[] SEGREDO =
            "segredo-de-teste-nao-usar-em-producao-0123456789".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PerfilRepository perfilRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void provisionarCriaParoquiaEARepeticaoDevolveAMesmaResposta() throws Exception {
        UUID contratacao = UUID.randomUUID();
        String slug = "prov-" + UUID.randomUUID();
        String email = "lucas-" + UUID.randomUUID() + "@teste.com";
        String corpo = corpoProvisionamento(contratacao, slug, email, "Paróquia São José");

        MvcResult primeira = mockMvc.perform(assinado(post("/integracao/v1/instancias"), "POST",
                        "/integracao/v1/instancias", corpo, UUID.randomUUID().toString(), agora())
                        .header("Idempotency-Key", contratacao.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.administrador.situacao").value("CONVIDADO"))
                .andReturn();
        String tenantId = json.readTree(primeira.getResponse().getContentAsString()).get("tenantId").asString();

        mockMvc.perform(assinado(post("/integracao/v1/instancias"), "POST",
                        "/integracao/v1/instancias", corpo, UUID.randomUUID().toString(), agora())
                        .header("Idempotency-Key", contratacao.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.tenantId").value(tenantId));

        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();
        assertThat(tenant.getId().toString()).isEqualTo(tenantId);
        assertThat(perfilRepository.findByTenantIdOrderByNomeAsc(tenant.getId()))
                .extracting(p -> p.getNome())
                .containsExactlyInAnyOrder("Administrador", "Secretário", "Coordenador");
        Usuario admin = usuarioRepository.findByEmail(email).orElseThrow();
        assertThat(admin.getSenhaHash()).isNull();
    }

    @Test
    void mesmaChaveComCorpoDiferenteDa409() throws Exception {
        UUID contratacao = UUID.randomUUID();
        String slug = "conf-" + UUID.randomUUID();
        String email = "conf-" + UUID.randomUUID() + "@teste.com";
        provisionar(contratacao, slug, email);

        String outro = corpoProvisionamento(contratacao, slug, email, "Outro nome");
        mockMvc.perform(assinado(post("/integracao/v1/instancias"), "POST",
                        "/integracao/v1/instancias", outro, UUID.randomUUID().toString(), agora())
                        .header("Idempotency-Key", contratacao.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCY_KEY_CONFLITO"));
    }

    @Test
    void duasRequisicoesSimultaneasComAMesmaChaveCriamUmaParoquiaSo() throws Exception {
        UUID contratacao = UUID.randomUUID();
        String slug = "conc-" + UUID.randomUUID();
        String corpo = corpoProvisionamento(contratacao, slug, "conc-" + UUID.randomUUID() + "@teste.com", "Concorrente");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> chamadas = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                chamadas.add(() -> mockMvc.perform(assinado(post("/integracao/v1/instancias"), "POST",
                                "/integracao/v1/instancias", corpo, UUID.randomUUID().toString(), agora())
                                .header("Idempotency-Key", contratacao.toString()))
                        .andReturn().getResponse().getStatus());
            }
            List<Integer> status = new ArrayList<>();
            for (Future<Integer> futuro : pool.invokeAll(chamadas)) {
                status.add(futuro.get());
            }
            assertThat(status).containsExactlyInAnyOrder(201, 200);
        } finally {
            pool.shutdown();
        }
        assertThat(tenantRepository.findBySlug(slug)).isPresent();
    }

    @Test
    void slugJaUsadoDa422() throws Exception {
        String slug = "slug-" + UUID.randomUUID();
        provisionar(UUID.randomUUID(), slug, "a-" + UUID.randomUUID() + "@teste.com");
        UUID outra = UUID.randomUUID();
        String corpo = corpoProvisionamento(outra, slug, "b-" + UUID.randomUUID() + "@teste.com", "Outra");

        mockMvc.perform(assinado(post("/integracao/v1/instancias"), "POST",
                        "/integracao/v1/instancias", corpo, UUID.randomUUID().toString(), agora())
                        .header("Idempotency-Key", outra.toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SLUG_EM_USO"));
    }

    @Test
    void nonceRepetidoTimestampVelhoEAssinaturaErradaSaoRecusados() throws Exception {
        String caminho = "/integracao/v1/instancias/" + UUID.randomUUID();
        String nonce = UUID.randomUUID().toString();
        String ts = agora();

        mockMvc.perform(assinado(get(caminho), "GET", caminho, "", nonce, ts))
                .andExpect(status().isNotFound());
        mockMvc.perform(assinado(get(caminho), "GET", caminho, "", nonce, ts))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NONCE_REPETIDO"));

        String velho = Long.toString(Instant.now().getEpochSecond() - 600);
        mockMvc.perform(assinado(get(caminho), "GET", caminho, "", UUID.randomUUID().toString(), velho))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TIMESTAMP_FORA_DA_JANELA"));

        mockMvc.perform(assinado(get(caminho), "GET", "/integracao/v1/outro", "", UUID.randomUUID().toString(), agora()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("ASSINATURA_INVALIDA"));
    }

    @Test
    void catalogoDeRecursosSoComAssinaturaEComOsCodigosDoContrato() throws Exception {
        String caminho = "/integracao/v1/recursos";

        mockMvc.perform(get(caminho)).andExpect(status().isUnauthorized());
        mockMvc.perform(assinado(get(caminho), "GET", caminho, "", UUID.randomUUID().toString(), agora()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'voluntarios')].tipo").value("LIMITE"))
                .andExpect(jsonPath("$[?(@.codigo == 'voluntarios')].unidade").value("pessoa"))
                .andExpect(jsonPath("$[?(@.codigo == 'ESCALAS')].tipo").value("FUNCIONALIDADE"))
                .andExpect(jsonPath("$[?(@.codigo == 'ESCALAS')].aplicado").value(true))
                .andExpect(jsonPath("$.length()").value(CatalogoDeRecursos.RECURSOS.size()));
    }

    @Test
    void webhookBloqueiaEVersaoAntigaNaoReverte() throws Exception {
        String email = "wh-" + UUID.randomUUID() + "@teste.com";
        UUID contratacao = UUID.randomUUID();
        String tenantId = provisionar(contratacao, "wh-" + UUID.randomUUID(), email);
        Usuario admin = usuarioRepository.findByEmail(email).orElseThrow();
        String token = jwtService.gerarAccessToken(admin.getId(), UUID.fromString(tenantId), UsuarioTenant.Role.ADMIN);
        mockMvc.perform(get("/voluntarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        String caminho = "/integracao/v1/instancias/" + tenantId + "/direitos";
        mockMvc.perform(assinado(put(caminho), "PUT", caminho, direitos(contratacao, 2, "BLOQUEADA", false),
                        UUID.randomUUID().toString(), agora()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(true));
        mockMvc.perform(get("/voluntarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(assinado(put(caminho), "PUT", caminho, direitos(contratacao, 1, "ATIVA", true),
                        UUID.randomUUID().toString(), agora()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(false))
                .andExpect(jsonPath("$.versaoAtual").value(2));
        mockMvc.perform(get("/voluntarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void codigoDeSuporteValeUmaVezEEntraEmParoquiaBloqueada() throws Exception {
        UUID contratacao = UUID.randomUUID();
        String tenantId = provisionar(contratacao, "sup-" + UUID.randomUUID(), "sup-" + UUID.randomUUID() + "@teste.com");
        String direitosCaminho = "/integracao/v1/instancias/" + tenantId + "/direitos";
        mockMvc.perform(assinado(put(direitosCaminho), "PUT", direitosCaminho,
                        direitos(contratacao, 2, "BLOQUEADA", false), UUID.randomUUID().toString(), agora()))
                .andExpect(status().isOk());

        String caminho = "/integracao/v1/instancias/" + tenantId + "/suporte";
        String pedido = "{\"operador\":{\"nome\":\"Gustavo\",\"email\":\"op@central.com\"},\"motivo\":\"Ajuda com escala\"}";
        MvcResult emitido = mockMvc.perform(assinado(post(caminho), "POST", caminho, pedido,
                        UUID.randomUUID().toString(), agora()))
                .andExpect(status().isCreated())
                .andReturn();
        String codigo = json.readTree(emitido.getResponse().getContentAsString()).get("codigo").asString();

        MvcResult troca = mockMvc.perform(post("/auth/suporte/trocar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = json.readTree(troca.getResponse().getContentAsString()).get("accessToken").asString();
        mockMvc.perform(get("/voluntarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/suporte/trocar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String provisionar(UUID contratacao, String slug, String email) throws Exception {
        String corpo = corpoProvisionamento(contratacao, slug, email, "Paróquia " + slug);
        MvcResult resultado = mockMvc.perform(assinado(post("/integracao/v1/instancias"), "POST",
                        "/integracao/v1/instancias", corpo, UUID.randomUUID().toString(), agora())
                        .header("Idempotency-Key", contratacao.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode resposta = json.readTree(resultado.getResponse().getContentAsString());
        return resposta.get("tenantId").asString();
    }

    private String corpoProvisionamento(UUID contratacao, String slug, String email, String nome) {
        return "{\"contratacaoId\":\"" + contratacao + "\","
                + "\"instancia\":{\"nome\":\"" + nome + "\",\"slug\":\"" + slug + "\"},"
                + "\"administrador\":{\"nome\":\"Lucas Fernando\",\"email\":\"" + email + "\"},"
                + "\"direitos\":" + direitos(contratacao, 1, "ATIVA", true) + "}";
    }

    private String direitos(UUID contratacao, int versao, String situacao, boolean liberado) {
        return "{\"contratacaoId\":\"" + contratacao + "\",\"produto\":\"SERVIREA\",\"versao\":" + versao
                + ",\"situacao\":\"" + situacao + "\",\"acessoLiberado\":" + liberado
                + ",\"plano\":{\"codigo\":\"PROFISSIONAL\",\"nome\":\"Profissional\"}"
                + ",\"limites\":{\"voluntarios\":100},\"funcionalidades\":[\"ESCALAS\"]}";
    }

    @Test
    void consumoExigeHmacValidoEDevolveApenasAgregados() throws Exception {
        String slug="consumo-"+UUID.randomUUID();
        String tenant=provisionar(UUID.randomUUID(),slug,slug+"@example.test");
        String caminho="/integracao/v1/instancias/"+tenant+"/consumo";
        mockMvc.perform(get(caminho)).andExpect(status().isUnauthorized());
        mockMvc.perform(assinado(get(caminho),"GET",caminho,"",UUID.randomUUID().toString(),agora()))
          .andExpect(status().isOk()).andExpect(jsonPath("$.versaoContrato").value(1))
          .andExpect(jsonPath("$.tenantId").value(tenant.toString()))
          .andExpect(jsonPath("$.consumo.itens.length()").value(7))
          .andExpect(jsonPath("$.pessoas").doesNotExist()).andExpect(jsonPath("$.administrador").doesNotExist());
    }

    private MockHttpServletRequestBuilder assinado(MockHttpServletRequestBuilder builder, String metodo,
                                                    String caminhoAssinado, String corpo, String nonce, String ts) {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        builder.header("X-Integracao-Chave", CHAVE)
                .header("X-Integracao-Timestamp", ts)
                .header("X-Integracao-Nonce", nonce)
                .header("X-Integracao-Assinatura",
                        HmacAssinatura.assinar(SEGREDO, metodo, caminhoAssinado, ts, nonce, bytes));
        if (!corpo.isEmpty()) {
            builder.contentType(MediaType.APPLICATION_JSON).content(bytes);
        }
        return builder;
    }

    private static String agora() {
        return Long.toString(Instant.now().getEpochSecond());
    }
}
