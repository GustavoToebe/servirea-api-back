package br.com.servire.api.notificacao;

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
class NotificacaoHttpIntegrationTest extends AbstractIntegrationTest {
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

    UUID tenant, usuario, escala, comWhats, semWhats, contaVinculada;
    String token;
    LocalDate dia;

    @BeforeEach
    void preparar() {
        String slug = UUID.randomUUID().toString();
        var paroquia = tenants.saveAndFlush(new Tenant(slug, slug, "Paróquia Teste", Tenant.Status.ATIVO));
        tenant = paroquia.getId();
        TenantContext.set(tenant);
        dia = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(15);
        var u = usuarios.saveAndFlush(new Usuario(slug + "@teste.test", "Coordenação"));
        usuario = u.getId();
        vinculos.saveAndFlush(new UsuarioTenant(u, paroquia, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO));
        var ana = pessoa("Ana", true, "(45) 99999-0001", "ana@teste.test");
        var beto = pessoa("Beto", false, "(45) 99999-0002", "beto@teste.test");
        comWhats = ana.getId();
        semWhats = beto.getId();
        var conta = usuarios.saveAndFlush(new Usuario("conta-" + slug + "@teste.test", "Conta da Ana"));
        var vinculo = new UsuarioTenant(conta, paroquia, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO);
        vinculo.setPessoaId(comWhats);
        vinculos.saveAndFlush(vinculo);
        contaVinculada = conta.getId();
        var s = new Escala("Escala de novembro", TipoEscala.MENSAL);
        s.setStatus(StatusEscala.FINALIZADA);
        var e = new EscalaEvento(dia, LocalTime.of(19, 0), "Missa");
        e.setEscala(s);
        vaga(e, FuncaoEscala.MISSAL, 1, ana);
        vaga(e, FuncaoEscala.CRUZ, 2, beto);
        s.getEventos().add(e);
        escala = escalas.saveAndFlush(s).getId();
        token = jwt.gerarAccessToken(usuario, tenant, UsuarioTenant.Role.ADMIN);
        TenantContext.clear();
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    Pessoa pessoa(String nome, boolean autoriza, String telefone, String email) {
        Pessoa p = Pessoas.voluntario(nome);
        p.getVoluntario().setAutorizaWhatsapp(autoriza);
        var t = new PessoaTelefone("celular", telefone, true);
        t.setPessoa(p);
        p.getTelefones().add(t);
        var m = new PessoaEmail("pessoal", email, true);
        m.setPessoa(p);
        p.getEmails().add(m);
        return pessoas.saveAndFlush(p);
    }

    void vaga(EscalaEvento e, FuncaoEscala f, int pos, Pessoa p) {
        var v = new EscalaVaga(f, pos);
        v.setEvento(e);
        v.setVoluntario(p.getVoluntario());
        e.getVagas().add(v);
    }

    JsonNode enviar(String caminho, Object corpo, int esperado) throws Exception {
        var r = mvc.perform(post(caminho).header("Authorization", "Bearer " + token).contentType("application/json")
                .content(json.writeValueAsString(corpo))).andExpect(status().is(esperado)).andReturn();
        String c = r.getResponse().getContentAsString();
        return c.isBlank() ? null : json.readTree(c);
    }

    JsonNode ler(String caminho) throws Exception {
        return json.readTree(mvc.perform(get(caminho).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    void configurar(String origem, String canal, boolean ativo, long versao, int esperado) throws Exception {
        mvc.perform(put("/notificacoes/configuracoes/" + origem + "/" + canal).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json.writeValueAsString(Map.of("ativo", ativo, "versao", versao))))
                .andExpect(status().is(esperado));
    }

    @Test
    void whatsappSoVaiParaQuemAutorizouEMesmaVersaoNaoRepete() throws Exception {
        var r = enviar("/escalas/" + escala + "/notificacoes", Map.of("canal", "WHATSAPP"), 200);
        assertThat(r.path("total").asInt()).isEqualTo(1);
        assertThat(r.path("ignorados").asInt()).isEqualTo(1);
        enviar("/escalas/" + escala + "/notificacoes", Map.of("canal", "WHATSAPP"), 409);
        var e = ler("/notificacoes/entregas").path("itens").get(0);
        assertThat(e.path("titulo").asString()).isEqualTo("Escala de novembro");
        assertThat(e.path("gatilho").asString()).isEqualTo("MANUAL");
        assertThat(e.path("pendentes").asLong()).isEqualTo(1);
    }

    @Test
    void emailVaiParaTodosComEmailECentroListaPorOrigem() throws Exception {
        assertThat(enviar("/escalas/" + escala + "/notificacoes", Map.of("canal", "EMAIL"), 200).path("total").asInt()).isEqualTo(2);
        assertThat(ler("/notificacoes/entregas?origem=ESCALA").path("total").asInt()).isEqualTo(1);
        assertThat(ler("/notificacoes/entregas?origem=MURAL").path("total").asInt()).isZero();
    }

    @Test
    void escalaEmRascunhoNaoNotifica() throws Exception {
        TenantContext.set(tenant);
        try {
            new TransactionTemplate(tm).executeWithoutResult(tx -> em.find(Escala.class, escala).setStatus(StatusEscala.RASCUNHO));
        } finally {
            TenantContext.clear();
        }
        enviar("/escalas/" + escala + "/notificacoes", Map.of("canal", "EMAIL"), 409);
    }

    @Test
    void gatilhoAutomaticoNasceDesligadoEFuncionaAoFinalizar() throws Exception {
        for (JsonNode c : ler("/notificacoes/configuracoes")) {
            assertThat(c.path("ativo").asBoolean()).isFalse();
        }
        reabrirEFinalizar();
        assertThat(ler("/notificacoes/entregas").path("total").asInt()).isZero();
        configurar("ESCALA", "EMAIL", true, 0, 200);
        configurar("ESCALA", "EMAIL", false, 0, 409);
        reabrirEFinalizar();
        var e = ler("/notificacoes/entregas").path("itens").get(0);
        assertThat(e.path("gatilho").asString()).isEqualTo("AUTOMATICO");
        assertThat(e.path("total").asInt()).isEqualTo(2);
    }

    void reabrirEFinalizar() throws Exception {
        enviar("/escalas/" + escala + "/reabrir", Map.of(), 200);
        enviar("/escalas/" + escala + "/finalizar", Map.of(), 200);
    }

    @Test
    void muralNotificaSoContasVinculadasEExigeVersaoAtual() throws Exception {
        var aviso = enviar("/mural/avisos", Map.of("titulo", "Reunião", "descricao", "Sábado às 9h", "status", "PUBLICADO"), 200);
        String id = aviso.path("id").asString();
        long versao = aviso.path("versao").asLong();
        enviar("/mural/avisos/" + id + "/notificacoes", Map.of("canal", "EMAIL", "versao", versao + 1), 409);
        var r = enviar("/mural/avisos/" + id + "/notificacoes", Map.of("canal", "EMAIL", "versao", versao), 200);
        assertThat(r.path("total").asInt()).isEqualTo(1);
        enviar("/mural/avisos/" + id + "/notificacoes", Map.of("canal", "EMAIL", "versao", versao), 409);
    }

    @Test
    void muralSelecionadosNotificaApenasOsEscolhidos() throws Exception {
        var aviso = enviar("/mural/avisos", Map.of("titulo", "Só equipe", "descricao", "x", "status", "PUBLICADO",
                "publico", "SELECIONADOS", "destinatarios", List.of(usuario)), 200);
        enviar("/mural/avisos/" + aviso.path("id").asString() + "/notificacoes",
                Map.of("canal", "EMAIL", "versao", aviso.path("versao").asLong()), 400);
    }

    @Test
    void muralPublicadoComGatilhoLigadoEnfileiraSozinho() throws Exception {
        configurar("MURAL", "EMAIL", true, 0, 200);
        enviar("/mural/avisos", Map.of("titulo", "Novo", "descricao", "texto", "status", "PUBLICADO"), 200);
        var e = ler("/notificacoes/entregas?origem=MURAL").path("itens").get(0);
        assertThat(e.path("gatilho").asString()).isEqualTo("AUTOMATICO");
        assertThat(e.path("total").asInt()).isEqualTo(1);
        enviar("/mural/avisos", Map.of("titulo", "Rascunho", "descricao", "texto", "status", "ARQUIVADO"), 200);
        assertThat(ler("/notificacoes/entregas?origem=MURAL").path("total").asInt()).isEqualTo(1);
    }

    @Test
    void permissaoPlanoEIsolamentoDeParoquia() throws Exception {
        var t = tenants.saveAndFlush(new Tenant(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "Externa", Tenant.Status.ATIVO));
        vinculos.saveAndFlush(new UsuarioTenant(usuarios.findById(usuario).orElseThrow(), t, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO));
        String externo = jwt.gerarAccessToken(usuario, t.getId(), UsuarioTenant.Role.ADMIN);
        mvc.perform(post("/escalas/" + escala + "/notificacoes").header("Authorization", "Bearer " + externo)
                .contentType("application/json").content("{\"canal\":\"EMAIL\"}")).andExpect(status().isNotFound());
        enviar("/escalas/" + escala + "/notificacoes", Map.of("canal", "EMAIL"), 200);
        mvc.perform(get("/notificacoes/entregas").header("Authorization", "Bearer " + externo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        var vinculo = vinculos.findByUsuario_IdAndTenant_Id(usuario, tenant).orElseThrow();
        vinculo.setRole(UsuarioTenant.Role.VISUALIZADOR);
        vinculos.saveAndFlush(vinculo);
        String leitor = jwt.gerarAccessToken(usuario, tenant, UsuarioTenant.Role.VISUALIZADOR);
        mvc.perform(post("/escalas/" + escala + "/notificacoes").header("Authorization", "Bearer " + leitor)
                .contentType("application/json").content("{\"canal\":\"EMAIL\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/notificacoes/configuracoes/ESCALA/EMAIL").header("Authorization", "Bearer " + leitor)
                .contentType("application/json").content("{\"ativo\":true,\"versao\":0}")).andExpect(status().isForbidden());
    }
}
