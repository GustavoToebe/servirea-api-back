package br.com.servire.api.web;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.inscricao.Inscricao;
import br.com.servire.api.inscricao.InscricaoEmail;
import br.com.servire.api.inscricao.InscricaoRepository;
import br.com.servire.api.inscricao.InscricaoResponsavel;
import br.com.servire.api.inscricao.InscricaoResponsavelEmail;
import br.com.servire.api.inscricao.InscricaoResponsavelTelefone;
import br.com.servire.api.inscricao.InscricaoTelefone;
import br.com.servire.api.security.JwtService;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxos reais via HTTP, com JWT de verdade e sem mock de service nem
 * {@code csrf()} do MockMvc — o que o front faz. Criado em 24/09/2026
 * depois de bugs que os testes de service não pegavam: escrita com Bearer
 * barrada por CSRF (403), {@code GET /pessoas} e {@code GET /pessoas/{id}}
 * com relações, toda a API de escalas estourando
 * {@code LazyInitializationException} no controller e a fila
 * {@code GET /inscricoes} com "Could not generate fetch" (500 sempre).
 * Todo endpoint de leitura novo deveria ganhar um caso aqui, com dados.
 */
@AutoConfigureMockMvc
class FluxoHttpIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioTenantRepository usuarioTenantRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private InscricaoRepository inscricaoRepository;

    private String token;
    private UUID tenantId;

    @BeforeEach
    void criarParoquiaEAdmin() {
        String sufixo = UUID.randomUUID().toString();
        Usuario usuario = usuarioRepository.saveAndFlush(new Usuario("fluxo-" + sufixo + "@teste.com", "Admin Fluxo"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "FLUXO-" + sufixo.substring(0, 8), "fluxo-" + sufixo, "Paróquia fluxo HTTP", Tenant.Status.ATIVO));
        usuarioTenantRepository.saveAndFlush(
                new UsuarioTenant(usuario, tenant, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO));
        token = jwtService.gerarAccessToken(usuario.getId(), tenant.getId(), UsuarioTenant.Role.ADMIN);
        tenantId = tenant.getId();
    }

    @AfterEach
    void limparTenantContext() {
        TenantContext.clear();
    }

    @Test
    void escritaComBearerNaoPrecisaDeTokenCsrfEFichaVoltaComRelacoes() throws Exception {
        String voluntarioJson = """
                {"papeis":["VOLUNTARIO"],"nomeCompleto":"Coroinha HTTP",
                 "emails":[],"telefones":[],
                 "responsaveis":[{"novaPessoa":{"nomeCompleto":"Mãe HTTP"},
                                  "parentesco":"Mãe","parentescoInverso":"Filho","principal":true}],
                 "voluntario":{"tipo":"COROINHA","ativo":true,"autorizaWhatsapp":false,"funcoesHabilitadas":["VELA"]}}
                """;
        String criado = mockMvc.perform(json(post("/pessoas"), voluntarioJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(criado, "$.id");
        String maeId = JsonPath.read(criado, "$.responsaveis[0].pessoaId");

        mockMvc.perform(autenticado(get("/pessoas/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsaveis[0].nomeCompleto").value("Mãe HTTP"))
                .andExpect(jsonPath("$.responsaveis[0].parentesco").value("Mãe"))
                .andExpect(jsonPath("$.dependentes", hasSize(0)));

        mockMvc.perform(autenticado(get("/pessoas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(autenticado(get("/pessoas").param("papel", "RESPONSAVEL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dependentes[0].nomeCompleto").value("Coroinha HTTP"));

        mockMvc.perform(autenticado(get("/pessoas/{id}", maeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependentes[0].nomeCompleto").value("Coroinha HTTP"))
                .andExpect(jsonPath("$.dependentes[0].parentesco").value("Filho"))
                .andExpect(jsonPath("$.dependentes[0].parentescoInverso").value("Mãe"));
    }

    @Test
    void rotaDoCookieDeRefreshContinuaExigindoCsrfMesmoComBearer() throws Exception {
        mockMvc.perform(autenticado(post("/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void escritaSemBearerContinuaExigindoCsrf() throws Exception {
        mockMvc.perform(post("/pessoas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cicloDaEscalaViaHttp() throws Exception {
        String voluntario = mockMvc.perform(json(post("/pessoas"), """
                        {"papeis":["VOLUNTARIO"],"nomeCompleto":"Acólito Escalado","emails":[],"telefones":[],
                         "voluntario":{"tipo":"ACOLITO","ativo":true,"autorizaWhatsapp":false,
                                       "funcoesHabilitadas":["MISSAL","VELA"]}}
                        """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String voluntarioId = JsonPath.read(voluntario, "$.id");
        mockMvc.perform(autenticado(get("/voluntarios")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nomeCompleto").value("Acólito Escalado"));

        String escalaJson = """
                {"titulo":"Escala HTTP","tipo":"MENSAL","ano":2026,"mes":10,"observacao":null,"version":%s,
                 "eventos":[{"data":"2026-10-04","horario":"09:30:00","celebracao":"Missa",
                             "vagas":[{"funcao":"MISSAL","posicao":1,"voluntarioId":"%s"},
                                      {"funcao":"VELA","posicao":1,"voluntarioId":null}]}]}
                """;
        String criada = mockMvc.perform(json(post("/escalas"), escalaJson.formatted("null", voluntarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos[0].vagas[0].voluntarioNome").value("Acólito Escalado"))
                .andReturn().getResponse().getContentAsString();
        String escalaId = JsonPath.read(criada, "$.id");
        Number version = JsonPath.read(criada, "$.version");

        mockMvc.perform(autenticado(get("/escalas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventos[0].vagas", hasSize(2)));
        mockMvc.perform(autenticado(get("/escalas").param("ano", "2026").param("mes", "10").param("tipo", "MENSAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(autenticado(get("/escalas").param("status", "FINALIZADA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        String atualizada = mockMvc.perform(json(put("/escalas/{id}", escalaId),
                        escalaJson.formatted(version.toString(), voluntarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos[0].vagas[0].voluntarioNome").value("Acólito Escalado"))
                .andReturn().getResponse().getContentAsString();
        String vagaId = JsonPath.read(atualizada, "$.eventos[0].vagas[0].id");

        mockMvc.perform(autenticado(post("/escalas/{id}/finalizar", escalaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZADA"));
        mockMvc.perform(json(patch("/escalas/vagas/{vagaId}/presenca", vagaId), "{\"presenca\":\"PRESENTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voluntarioNome").value("Acólito Escalado"));
        mockMvc.perform(autenticado(get("/escalas/{id}", escalaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos[0].vagas[0].presenca").value("PRESENTE"));
    }

    @Test
    void tenantComVariosContatosNaoDuplicaLinhas() throws Exception {
        mockMvc.perform(json(put("/tenant"), """
                        {"nome":"Paróquia Contatos","emails":[
                           {"tipo":"Secretaria","email":"a@paroquia.org","principal":true},
                           {"tipo":"Padre","email":"b@paroquia.org","principal":false}],
                         "telefones":[
                           {"tipo":"Fixo","numero":"4533330000","principal":true},
                           {"tipo":"Celular","numero":"45999990000","principal":false}]}
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(autenticado(get("/tenant")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emails", hasSize(2)))
                .andExpect(jsonPath("$.telefones", hasSize(2)));
    }

    @Test
    void filaDeInscricoesComVariosContatosEResponsaveisViaHttp() throws Exception {
        TenantContext.set(tenantId);
        Inscricao inscricao = new Inscricao("Candidato Com Família");
        for (int i = 0; i < 2; i++) {
            InscricaoEmail email = new InscricaoEmail("E-mail " + i, "cand" + i + "-" + UUID.randomUUID() + "@teste.com", i == 0);
            email.setInscricao(inscricao);
            inscricao.getEmails().add(email);
            InscricaoTelefone telefone = new InscricaoTelefone("Tel " + i, "4599999000" + i, i == 0);
            telefone.setInscricao(inscricao);
            inscricao.getTelefones().add(telefone);
            InscricaoResponsavel responsavel = new InscricaoResponsavel(i == 0 ? "Mãe" : "Pai", "Responsável " + i, i == 0);
            responsavel.setInscricao(inscricao);
            for (int j = 0; j < 2; j++) {
                InscricaoResponsavelEmail re = new InscricaoResponsavelEmail("E-mail " + j,
                        "resp" + i + j + "-" + UUID.randomUUID() + "@teste.com", j == 0);
                re.setResponsavel(responsavel);
                responsavel.getEmails().add(re);
                InscricaoResponsavelTelefone rt = new InscricaoResponsavelTelefone("Tel " + j, "459888800" + i + j, j == 0);
                rt.setResponsavel(responsavel);
                responsavel.getTelefones().add(rt);
            }
            inscricao.getResponsaveis().add(responsavel);
        }
        UUID id = inscricaoRepository.saveAndFlush(inscricao).getId();
        TenantContext.clear();

        mockMvc.perform(autenticado(get("/inscricoes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].emails", hasSize(2)))
                .andExpect(jsonPath("$[0].telefones", hasSize(2)))
                .andExpect(jsonPath("$[0].responsaveis", hasSize(2)))
                .andExpect(jsonPath("$[0].responsaveis[0].emails", hasSize(2)))
                .andExpect(jsonPath("$[0].responsaveis[0].telefones", hasSize(2)));
        mockMvc.perform(autenticado(get("/inscricoes").param("status", "PENDENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(autenticado(get("/inscricoes/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emails", hasSize(2)))
                .andExpect(jsonPath("$.responsaveis", hasSize(2)))
                .andExpect(jsonPath("$.responsaveis[1].telefones", hasSize(2)));

        String aprovada = mockMvc.perform(autenticado(post("/inscricoes/{id}/aprovar", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andReturn().getResponse().getContentAsString();
        String voluntarioId = JsonPath.read(aprovada, "$.voluntarioId");
        mockMvc.perform(autenticado(get("/pessoas/{id}", voluntarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emails", hasSize(2)))
                .andExpect(jsonPath("$.responsaveis", hasSize(2)));
    }

    private MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).accept(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String corpo) {
        return autenticado(request).contentType(MediaType.APPLICATION_JSON).content(corpo);
    }
}
