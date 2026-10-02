package br.com.servire.api.escala;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class DistribuicaoHttpIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired UsuarioRepository usuarios;
    @Autowired UsuarioTenantRepository vinculos;
    @Autowired PessoaRepository pessoas;
    @Autowired VoluntarioRepository voluntarios;
    @Autowired EscalaRepository escalas;
    @Autowired JwtService jwt;
    @Autowired JsonMapper json;
    @Autowired PlatformTransactionManager tm;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;

    UUID tenant, usuario, escala, a, b;
    String token;
    LocalDate dia;
    static final Map<String, Object> REGRAS = Map.of("maximoPorPessoa", 3, "intervaloDias", 0, "exigirResposta", false);

    @BeforeEach
    void preparar() {
        String slug = UUID.randomUUID().toString();
        var paroquia = tenants.saveAndFlush(new Tenant(slug, slug, "Distribuição", Tenant.Status.ATIVO));
        tenant = paroquia.getId();
        TenantContext.set(tenant);
        dia = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(20);
        var u = usuarios.saveAndFlush(new Usuario(slug + "@teste.test", "Coordenação"));
        usuario = u.getId();
        vinculos.saveAndFlush(new UsuarioTenant(u, paroquia, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO));
        a = voluntario("Ana");
        b = voluntario("Beto");
        var s = new Escala("Escala de teste", TipoEscala.MENSAL);
        s.setStatus(StatusEscala.RASCUNHO);
        evento(s, dia, 19);
        evento(s, dia.plusDays(7), 19);
        escala = escalas.saveAndFlush(s).getId();
        token = jwt.gerarAccessToken(usuario, tenant, UsuarioTenant.Role.ADMIN);
        TenantContext.clear();
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    UUID voluntario(String nome) {
        var p = pessoas.saveAndFlush(new Pessoa(PessoaPapel.VOLUNTARIO, nome));
        var v = new Voluntario();
        v.setPessoa(p);
        v.setTipo(TipoVoluntario.COROINHA);
        v.setFuncoesHabilitadas(new FuncaoEscala[]{FuncaoEscala.MISSAL});
        voluntarios.saveAndFlush(v);
        return p.getId();
    }

    void evento(Escala s, LocalDate data, int hora) {
        var e = new EscalaEvento(data, LocalTime.of(hora, 0), "Missa");
        e.setEscala(s);
        var vaga = new EscalaVaga(FuncaoEscala.MISSAL, 1);
        vaga.setEvento(e);
        e.getVagas().add(vaga);
        s.getEventos().add(e);
    }

    JsonNode previa() throws Exception {
        return json.readTree(mvc.perform(post("/escalas/" + escala + "/distribuicao/previa")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(json.writeValueAsString(Map.of("regras", REGRAS))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    int aplicar(JsonNode p, long versao) throws Exception {
        var escolhas = new ArrayList<Map<String, Object>>();
        for (JsonNode s : p.path("sugestoes")) {
            escolhas.add(Map.of("vagaId", s.path("vagaId").asString(), "pessoaId", s.path("pessoaId").asString()));
        }
        return mvc.perform(post("/escalas/" + escala + "/distribuicao/aplicacao")
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("versao", versao, "regras", REGRAS, "escolhas", escolhas))))
                .andReturn().getResponse().getStatus();
    }

    List<UUID> alocados() {
        TenantContext.set(tenant);
        try {
            return new TransactionTemplate(tm).execute(tx -> em.createQuery(
                            "select v.voluntario.id from EscalaVaga v where v.evento.escala.id=:id and v.voluntario is not null",
                            UUID.class).setParameter("id", escala).getResultList());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void previaNaoGravaEExplicaCadaSugestao() throws Exception {
        var p = previa();
        assertThat(p.path("sugestoes").size()).isEqualTo(2);
        assertThat(p.path("sugestoes").get(0).path("explicacao").asString()).contains("Menor carga");
        assertThat(p.path("sugestoes").get(0).path("pessoaId").asString())
                .isNotEqualTo(p.path("sugestoes").get(1).path("pessoaId").asString());
        assertThat(alocados()).isEmpty();
    }

    @Test
    void aplicacaoGravaEVersaoAntigaNaoSeReaplica() throws Exception {
        var p = previa();
        long versao = p.path("versao").asLong();
        assertThat(aplicar(p, versao)).isEqualTo(200);
        assertThat(alocados()).containsExactlyInAnyOrder(a, b);
        assertThat(aplicar(p, versao)).isEqualTo(409);
    }

    @Test
    void indisponibilidadeNovaInvalidaPreviaAntiga() throws Exception {
        var p = previa();
        String antes = p.path("sugestoes").get(0).path("pessoaId").asString();
        UUID indisponivel = UUID.fromString(antes);
        TenantContext.set(tenant);
        try {
            new TransactionTemplate(tm).executeWithoutResult(tx -> {
                for (JsonNode s : p.path("sugestoes")) {
                    if (s.path("pessoaId").asString().equals(antes)) {
                        em.persist(new Indisponibilidade(indisponivel, LocalDate.parse(s.path("data").asString()), null, null));
                    }
                }
            });
        } finally {
            TenantContext.clear();
        }
        assertThat(aplicar(p, p.path("versao").asLong())).isEqualTo(409);
        assertThat(alocados()).isEmpty();
    }

    @Test
    void escolhaForaDoMotorEhRecusada() throws Exception {
        var p = previa();
        String vaga = p.path("sugestoes").get(0).path("vagaId").asString();
        var corpo = Map.of("versao", p.path("versao").asLong(), "regras", REGRAS,
                "escolhas", List.of(Map.of("vagaId", vaga, "pessoaId", UUID.randomUUID().toString())));
        mvc.perform(post("/escalas/" + escala + "/distribuicao/aplicacao").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json.writeValueAsString(corpo))).andExpect(status().isConflict());
        assertThat(alocados()).isEmpty();
    }

    @Test
    void escalaFinalizadaPermissaoERegrasInvalidasSaoProtegidas() throws Exception {
        mvc.perform(post("/escalas/" + escala + "/distribuicao/previa").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"regras\":{\"maximoPorPessoa\":0,\"intervaloDias\":0}}"))
                .andExpect(status().isBadRequest());
        TenantContext.set(tenant);
        try {
            new TransactionTemplate(tm).executeWithoutResult(tx -> em.find(Escala.class, escala).setStatus(StatusEscala.FINALIZADA));
        } finally {
            TenantContext.clear();
        }
        mvc.perform(post("/escalas/" + escala + "/distribuicao/previa").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json.writeValueAsString(Map.of("regras", REGRAS))))
                .andExpect(status().isConflict());
        var vinculo = vinculos.findByUsuario_IdAndTenant_Id(usuario, tenant).orElseThrow();
        vinculo.setRole(UsuarioTenant.Role.VISUALIZADOR);
        vinculos.saveAndFlush(vinculo);
        String leitor = jwt.gerarAccessToken(usuario, tenant, UsuarioTenant.Role.VISUALIZADOR);
        mvc.perform(post("/escalas/" + escala + "/distribuicao/previa").header("Authorization", "Bearer " + leitor)
                .contentType("application/json").content(json.writeValueAsString(Map.of("regras", REGRAS))))
                .andExpect(status().isForbidden());
    }

    @Test
    void outraParoquiaNaoEnxergaAEscala() throws Exception {
        var t = tenants.saveAndFlush(new Tenant(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "Externa",
                Tenant.Status.ATIVO));
        vinculos.saveAndFlush(new UsuarioTenant(usuarios.findById(usuario).orElseThrow(), t, UsuarioTenant.Role.ADMIN,
                UsuarioTenant.Status.ATIVO));
        String externo = jwt.gerarAccessToken(usuario, t.getId(), UsuarioTenant.Role.ADMIN);
        mvc.perform(post("/escalas/" + escala + "/distribuicao/previa").header("Authorization", "Bearer " + externo)
                .contentType("application/json").content(json.writeValueAsString(Map.of("regras", REGRAS))))
                .andExpect(status().isNotFound());
    }
}
