package br.com.servire.api.checkin;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.*;
import br.com.servire.api.escala.*;
import br.com.servire.api.pessoa.*;
import br.com.servire.api.security.JwtService;
import br.com.servire.api.tenant.*;
import br.com.servire.api.voluntario.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class CheckinHttpIntegrationTest extends AbstractIntegrationTest {
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired UsuarioRepository usuarios;
    @Autowired UsuarioTenantRepository vinculos;
    @Autowired PessoaRepository pessoas;
    @Autowired EscalaRepository escalas;
    @Autowired JwtService jwt;
    @Autowired JsonMapper json;
    @Autowired PlatformTransactionManager tm;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;

    UUID tenant, coordenacao, voluntarioUsuario, outroUsuario, pessoa, evento, vaga, escala;
    String tokenCoord, tokenVoluntario, tokenOutro;

    @BeforeEach
    void preparar() {
        String slug = UUID.randomUUID().toString();
        var paroquia = tenants.saveAndFlush(new Tenant(slug, slug, "Check-in", Tenant.Status.ATIVO));
        tenant = paroquia.getId();
        TenantContext.set(tenant);
        var coord = usuarios.saveAndFlush(new Usuario(slug + "@teste.test", "Coordenação"));
        var vol = usuarios.saveAndFlush(new Usuario("v-" + slug + "@teste.test", "Voluntária"));
        var outro = usuarios.saveAndFlush(new Usuario("o-" + slug + "@teste.test", "Outra"));
        coordenacao = coord.getId();
        voluntarioUsuario = vol.getId();
        outroUsuario = outro.getId();
        var ana = Pessoas.voluntario("Ana");
        ana.getVoluntario().setFuncoesHabilitadas(new FuncaoEscala[]{FuncaoEscala.MISSAL});
        ana = pessoas.saveAndFlush(ana);
        var bia = pessoas.saveAndFlush(Pessoas.voluntario("Bia"));
        pessoa = ana.getId();
        vinculos.saveAndFlush(new UsuarioTenant(coord, paroquia, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO));
        var vv = new UsuarioTenant(vol, paroquia, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO);
        vv.setPessoaId(ana.getId());
        vinculos.saveAndFlush(vv);
        var vo = new UsuarioTenant(outro, paroquia, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO);
        vo.setPessoaId(bia.getId());
        vinculos.saveAndFlush(vo);
        var s = new Escala("Escala do mês", TipoEscala.MENSAL);
        s.setStatus(StatusEscala.FINALIZADA);
        LocalDateTime inicio = LocalDateTime.now(BRASILIA).plusMinutes(30);
        var e = new EscalaEvento(inicio.toLocalDate(), inicio.toLocalTime().withNano(0), "Missa");
        e.setEscala(s);
        var v = new EscalaVaga(FuncaoEscala.MISSAL, 1);
        v.setEvento(e);
        v.setVoluntario(ana.getVoluntario());
        e.getVagas().add(v);
        s.getEventos().add(e);
        escala = escalas.saveAndFlush(s).getId();
        evento = s.getEventos().getFirst().getId();
        vaga = s.getEventos().getFirst().getVagas().getFirst().getId();
        tokenCoord = jwt.gerarAccessToken(coordenacao, tenant, UsuarioTenant.Role.ADMIN);
        tokenVoluntario = jwt.gerarAccessToken(voluntarioUsuario, tenant, UsuarioTenant.Role.ADMIN);
        tokenOutro = jwt.gerarAccessToken(outroUsuario, tenant, UsuarioTenant.Role.ADMIN);
        TenantContext.clear();
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    String abrir(int minutos) throws Exception {
        var r = json.readTree(mvc.perform(post("/escalas/eventos/" + evento + "/checkin").header("Authorization", "Bearer " + tokenCoord)
                .contentType("application/json").content("{\"minutos\":" + minutos + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return r.path("token").asString();
    }

    int registrar(String token, String bearer) throws Exception {
        return mvc.perform(post("/portal/checkin").header("Authorization", "Bearer " + bearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("token", token)))).andReturn().getResponse().getStatus();
    }

    JsonNode registrarOk(String token) throws Exception {
        return json.readTree(mvc.perform(post("/portal/checkin").header("Authorization", "Bearer " + tokenVoluntario)
                .contentType("application/json").content(json.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    Presenca presenca() {
        TenantContext.set(tenant);
        try {
            return new TransactionTemplate(tm).execute(tx -> em.find(EscalaVaga.class, vaga).getPresenca());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void escaladoRegistraUmaVezEPresencaFicaPresente() throws Exception {
        String token = abrir(120);
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);
        var r = registrarOk(token);
        assertThat(r.path("jaRegistrado").asBoolean()).isFalse();
        assertThat(r.path("funcao").asString()).isEqualTo("MISSAL");
        assertThat(presenca()).isEqualTo(Presenca.PRESENTE);
        assertThat(registrarOk(token).path("jaRegistrado").asBoolean()).isTrue();
        var estado = json.readTree(mvc.perform(get("/escalas/eventos/" + evento + "/checkin").header("Authorization", "Bearer " + tokenCoord))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(estado.path("ativa").asBoolean()).isTrue();
        assertThat(estado.path("registros").size()).isEqualTo(1);
        assertThat(estado.path("registros").get(0).path("nome").asString()).isEqualTo("Ana");
        assertThat(estado.path("presentes").asInt()).isEqualTo(1);
    }

    @Test
    void tokenNaoFicaGravadoEOutraPessoaNaoEscaladaNaoRegistra() throws Exception {
        String token = abrir(60);
        TenantContext.set(tenant);
        try {
            var hashes = new TransactionTemplate(tm).execute(tx -> em.createQuery("select s.tokenHash from CheckinSessao s", String.class).getResultList());
            assertThat(hashes).hasSize(1);
            assertThat(hashes.getFirst()).hasSize(64).isNotEqualTo(token);
        } finally {
            TenantContext.clear();
        }
        assertThat(registrar(token, tokenOutro)).isEqualTo(404);
        assertThat(presenca()).isEqualTo(Presenca.PENDENTE);
    }

    @Test
    void codigoInvalidoRevogadoOuSubstituidoNaoFunciona() throws Exception {
        assertThat(registrar("x".repeat(43), tokenVoluntario)).isEqualTo(404);
        String antigo = abrir(60);
        String novo = abrir(60);
        assertThat(registrar(antigo, tokenVoluntario)).isEqualTo(404);
        mvc.perform(delete("/escalas/eventos/" + evento + "/checkin").header("Authorization", "Bearer " + tokenCoord)).andExpect(status().isNoContent());
        assertThat(registrar(novo, tokenVoluntario)).isEqualTo(404);
        assertThat(presenca()).isEqualTo(Presenca.PENDENTE);
    }

    @Test
    void codigoExpiradoNaoFunciona() throws Exception {
        String token = abrir(60);
        TenantContext.set(tenant);
        try {
            new TransactionTemplate(tm).executeWithoutResult(tx -> em.createQuery("update CheckinSessao s set s.expiraEm=:t")
                    .setParameter("t", Instant.now().minusSeconds(5)).executeUpdate());
        } finally {
            TenantContext.clear();
        }
        assertThat(registrar(token, tokenVoluntario)).isEqualTo(404);
    }

    @Test
    void faltaMarcadaPelaCoordenacaoNaoESobrescrita() throws Exception {
        String token = abrir(60);
        TenantContext.set(tenant);
        try {
            new TransactionTemplate(tm).executeWithoutResult(tx -> em.find(EscalaVaga.class, vaga).setPresenca(Presenca.FALTOU));
        } finally {
            TenantContext.clear();
        }
        assertThat(registrar(token, tokenVoluntario)).isEqualTo(409);
        assertThat(presenca()).isEqualTo(Presenca.FALTOU);
    }

    @Test
    void antesDaJanelaEEscalaNaoFinalizadaSaoRecusados() throws Exception {
        String token = abrir(60);
        TenantContext.set(tenant);
        try {
            new TransactionTemplate(tm).executeWithoutResult(tx -> {
                var e = em.find(EscalaEvento.class, evento);
                e.setData(LocalDate.now(BRASILIA).plusDays(2));
            });
        } finally {
            TenantContext.clear();
        }
        assertThat(registrar(token, tokenVoluntario)).isEqualTo(409);
        TenantContext.set(tenant);
        try {
            new TransactionTemplate(tm).executeWithoutResult(tx -> em.find(Escala.class, escala).setStatus(StatusEscala.RASCUNHO));
        } finally {
            TenantContext.clear();
        }
        mvc.perform(post("/escalas/eventos/" + evento + "/checkin").header("Authorization", "Bearer " + tokenCoord)
                .contentType("application/json").content("{}")).andExpect(status().isConflict());
        assertThat(registrar(token, tokenVoluntario)).isEqualTo(404);
    }

    @Test
    void permissaoValidacaoEIsolamentoDeParoquia() throws Exception {
        mvc.perform(post("/escalas/eventos/" + evento + "/checkin").header("Authorization", "Bearer " + tokenCoord)
                .contentType("application/json").content("{\"minutos\":5}")).andExpect(status().isBadRequest());
        String token = abrir(60);
        var t = tenants.saveAndFlush(new Tenant(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "Externa", Tenant.Status.ATIVO));
        vinculos.saveAndFlush(new UsuarioTenant(usuarios.findById(voluntarioUsuario).orElseThrow(), t, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO));
        String externo = jwt.gerarAccessToken(voluntarioUsuario, t.getId(), UsuarioTenant.Role.ADMIN);
        assertThat(registrar(token, externo)).isEqualTo(404);
        mvc.perform(post("/escalas/eventos/" + evento + "/checkin").header("Authorization", "Bearer " + externo)
                .contentType("application/json").content("{}")).andExpect(status().isNotFound());
        var vinculo = vinculos.findByUsuario_IdAndTenant_Id(coordenacao, tenant).orElseThrow();
        vinculo.setRole(UsuarioTenant.Role.VISUALIZADOR);
        vinculos.saveAndFlush(vinculo);
        String leitor = jwt.gerarAccessToken(coordenacao, tenant, UsuarioTenant.Role.VISUALIZADOR);
        mvc.perform(post("/escalas/eventos/" + evento + "/checkin").header("Authorization", "Bearer " + leitor)
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
}
