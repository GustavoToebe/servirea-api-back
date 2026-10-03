package br.com.servire.api.portal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.*;
import br.com.servire.api.calendario.*;
import br.com.servire.api.escala.*;
import br.com.servire.api.evento.*;
import br.com.servire.api.integracao.*;
import br.com.servire.api.pessoa.*;
import br.com.servire.api.security.JwtService;
import br.com.servire.api.tenant.*;
import br.com.servire.api.voluntario.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
class TrocaHttpIntegrationTest extends AbstractIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired TenantRepository tenants;
  @Autowired UsuarioRepository usuarios;
  @Autowired UsuarioTenantRepository vinculos;
  @Autowired PessoaRepository pessoas;
  @Autowired VoluntarioRepository voluntarios;
  @Autowired EscalaRepository escalas;
  @Autowired EventoRepository eventos;
  @Autowired EventoInscricaoRepository inscritos;
  @Autowired DireitosLocaisRepository direitos;
  @Autowired JwtService jwt;
  @Autowired JsonMapper json;
  @Autowired CalendarioRepository calendarios;
  @Autowired PlatformTransactionManager tm;
  @Autowired EscalaService escalaService;
  UUID tenant, usuario, pessoa, outroUsuario;
  String token;
  LocalDate dia;

  @BeforeEach
  void preparar() {
    doCallRealMethod().when(funcionalidadesPlano).liberadas();
    String slug = UUID.randomUUID().toString();
    var paroquia = tenants.saveAndFlush(new Tenant(slug, slug, "Portal", Tenant.Status.ATIVO));
    tenant = paroquia.getId();
    TenantContext.set(tenant);
    dia = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(10);
    var u = usuarios.saveAndFlush(new Usuario(slug + "@teste.test", "Conta A"));
    usuario = u.getId();
    var v = usuarios.saveAndFlush(new Usuario("b-" + slug + "@teste.test", "Conta B"));
    outroUsuario = v.getId();
    new TransactionTemplate(tm)
        .executeWithoutResult(
            tx -> {
              var a = pessoa("Pessoa A");
              var b = pessoa("Pessoa B");
              pessoa = a.getId();
              var va =
                  new UsuarioTenant(
                      u, paroquia, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO);
              va.setPessoaId(a.getId());
              vinculos.saveAndFlush(va);
              var vb =
                  new UsuarioTenant(
                      v, paroquia, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO);
              vb.setPessoaId(b.getId());
              vinculos.saveAndFlush(vb);
              escala(a, StatusEscala.FINALIZADA, false, "Missa A");
              escala(b, StatusEscala.FINALIZADA, false, "Missa B");
              escala(a, StatusEscala.RASCUNHO, false, "Rascunho privado");
              escala(a, StatusEscala.FINALIZADA, true, "Linha de referência");
              evento(a, "Evento A", Evento.Situacao.PUBLICADO);
              evento(b, "Evento B", Evento.Situacao.PUBLICADO);
              evento(a, "Rascunho evento", Evento.Situacao.RASCUNHO);
            });
    plano("PORTAL_VOLUNTARIO", "CALENDARIO", "MURAL", "PASTORAIS", "ESCALAS");
    token = jwt.gerarAccessToken(usuario, tenant, UsuarioTenant.Role.ADMIN);
    TenantContext.clear();
    vagaParaTroca();
  }

  Pessoa pessoa(String nome) {
    var p = pessoas.saveAndFlush(new Pessoa(PessoaPapel.VOLUNTARIO, nome));
    var v = new Voluntario();
    v.setPessoa(p);
    v.setTipo(TipoVoluntario.COROINHA);
    v.setFuncoesHabilitadas(new FuncaoEscala[] {FuncaoEscala.MISSAL});
    voluntarios.saveAndFlush(v);
    return p;
  }

  void escala(Pessoa p, StatusEscala status, boolean referencia, String titulo) {
    var s = new Escala(titulo, TipoEscala.MENSAL);
    s.setStatus(status);
    var e = new EscalaEvento(dia, LocalTime.of(19, 0), titulo);
    e.setEscala(s);
    e.setReferencia(referencia);
    s.getEventos().add(e);
    var vaga = new EscalaVaga(FuncaoEscala.values()[0], 1);
    vaga.setEvento(e);
    vaga.setVoluntario(voluntarios.findById(p.getId()).orElseThrow());
    e.getVagas().add(vaga);
    escalas.saveAndFlush(s);
  }

  void evento(Pessoa p, String titulo, Evento.Situacao status) {
    var e = new Evento(titulo, dia.atTime(20, 0));
    e.setSituacao(status);
    eventos.saveAndFlush(e);
    inscritos.saveAndFlush(new EventoInscricao(e.getId(), p, false));
  }

  void plano(String... codigos) {
    var d =
        direitos.findById(tenant).orElseGet(() -> new DireitosLocais(tenant, UUID.randomUUID()));
    d.setSituacao("ATIVA");
    d.setAcessoLiberado(true);
    d.setConfirmadoEm(Instant.now());
    d.setFuncionalidades(codigos);
    direitos.saveAndFlush(d);
  }

  String assinatura() throws Exception {
    return json.readTree(
            mvc.perform(post("/calendario/assinatura").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .path("token")
        .asString();
  }

  @AfterEach
  void limpar() {
    TenantContext.clear();
  }

  @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
  UUID vaga, substituto;
  String tokenB;

  void vagaParaTroca() {
    TenantContext.set(tenant);
    try {
      new TransactionTemplate(tm)
          .executeWithoutResult(
              tx -> {
                substituto =
                    vinculos
                        .findByUsuario_IdAndTenant_Id(outroUsuario, tenant)
                        .orElseThrow()
                        .getPessoaId();
                var e =
                    em.createQuery(
                            "select e from EscalaEvento e where e.celebracao=:nome",
                            EscalaEvento.class)
                        .setParameter("nome", "Missa A")
                        .getSingleResult();
                e.setHorario(LocalTime.of(21, 0));
                vaga = e.getVagas().getFirst().getId();
              });
    } finally {
      TenantContext.clear();
    }
    tokenB = jwt.gerarAccessToken(outroUsuario, tenant, UsuarioTenant.Role.ADMIN);
  }

  tools.jackson.databind.JsonNode pedir() throws Exception {
    return json.readTree(
        mvc.perform(
                post("/portal/vagas/" + vaga + "/trocas")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(Map.of("substitutoId", substituto, "versao", 0))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  UUID id(tools.jackson.databind.JsonNode p) {
    return UUID.fromString(p.path("id").asString());
  }

  int acao(String caminho, String bearer, boolean aprovar, long versao) throws Exception {
    return mvc.perform(
            put(caminho)
                .header("Authorization", "Bearer " + bearer)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("aprovar", aprovar, "versao", versao))))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  void alocacao(UUID esperada) {
    TenantContext.set(tenant);
    try {
      new TransactionTemplate(tm)
          .executeWithoutResult(
              tx -> {
                var v = em.find(EscalaVaga.class, vaga);
                assertThat(v.getVoluntario().getId()).isEqualTo(esperada);
                assertThat(v.getPresenca()).isEqualTo(Presenca.PENDENTE);
                assertThat(v.getResposta()).isEqualTo(RespostaParticipacao.PENDENTE);
              });
    } finally {
      TenantContext.clear();
    }
  }

  UUID escalaId() {
    TenantContext.set(tenant);
    try {
      return new TransactionTemplate(tm)
          .execute(tx -> em.find(EscalaVaga.class, vaga).getEvento().getEscala().getId());
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void trocaMantemOriginalAteAceiteEAprovacao() throws Exception {
    var p = pedir();
    alocacao(pessoa);
    assertThat(acao("/escalas/trocas/" + id(p) + "/decisao", token, true, 0)).isEqualTo(409);
    assertThat(acao("/portal/trocas/" + id(p) + "/aceite", tokenB, true, 0)).isEqualTo(200);
    alocacao(pessoa);
    assertThat(acao("/escalas/trocas/" + id(p) + "/decisao", token, true, 1)).isEqualTo(200);
    alocacao(substituto);
    mvc.perform(get("/portal/trocas").header("Authorization", "Bearer " + tokenB))
        .andExpect(jsonPath("$.itens[0].situacao").value("APROVADA"));
  }

  @Test
  void somenteSubstitutoPodeAceitarESomenteSolicitantePodeCancelar() throws Exception {
    var p = pedir();
    assertThat(acao("/portal/trocas/" + id(p) + "/aceite", token, true, 0)).isEqualTo(404);
    mvc.perform(
            put("/portal/trocas/" + id(p) + "/cancelamento")
                .header("Authorization", "Bearer " + tokenB)
                .contentType("application/json")
                .content("{\"versao\":0}"))
        .andExpect(status().isNotFound());
    alocacao(pessoa);
  }

  @Test
  void recusaDoSubstitutoEncerraSemAlterarEscala() throws Exception {
    var p = pedir();
    assertThat(acao("/portal/trocas/" + id(p) + "/aceite", tokenB, false, 0)).isEqualTo(200);
    assertThat(acao("/escalas/trocas/" + id(p) + "/decisao", token, true, 1)).isEqualTo(409);
    alocacao(pessoa);
  }

  @Test
  void recusaDaCoordenacaoEncerraSemAlterarEscala() throws Exception {
    var p = pedir();
    assertThat(acao("/portal/trocas/" + id(p) + "/aceite", tokenB, true, 0)).isEqualTo(200);
    assertThat(acao("/escalas/trocas/" + id(p) + "/decisao", token, false, 1)).isEqualTo(200);
    alocacao(pessoa);
  }

  @Test
  void cancelamentoDepoisDoAceiteImpedeAprovacao() throws Exception {
    var p = pedir();
    assertThat(acao("/portal/trocas/" + id(p) + "/aceite", tokenB, true, 0)).isEqualTo(200);
    mvc.perform(
            put("/portal/trocas/" + id(p) + "/cancelamento")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"versao\":1}"))
        .andExpect(status().isOk());
    assertThat(acao("/escalas/trocas/" + id(p) + "/decisao", token, true, 1)).isEqualTo(409);
    alocacao(pessoa);
  }

  @Test
  void duasAprovacoesConcorrentesSoAplicamUmaTroca() throws Exception {
    var p = pedir();
    assertThat(acao("/portal/trocas/" + id(p) + "/aceite", tokenB, true, 0)).isEqualTo(200);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> acao("/escalas/trocas/" + id(p) + "/decisao", token, true, 1));
      var b = pool.submit(() -> acao("/escalas/trocas/" + id(p) + "/decisao", token, true, 1));
      assertThat(
              List.of(
                  a.get(20, java.util.concurrent.TimeUnit.SECONDS),
                  b.get(20, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    alocacao(substituto);
  }

  @Test
  void disponibilidadeAlteradaDepoisDoAceiteBloqueiaAprovacao() throws Exception {
    var p = pedir();
    assertThat(acao("/portal/trocas/" + id(p) + "/aceite", tokenB, true, 0)).isEqualTo(200);
    TenantContext.set(tenant);
    try {
      new TransactionTemplate(tm)
          .executeWithoutResult(
              tx -> em.persist(new Indisponibilidade(substituto, dia, null, null)));
    } finally {
      TenantContext.clear();
    }
    assertThat(acao("/escalas/trocas/" + id(p) + "/decisao", token, true, 1)).isEqualTo(409);
    alocacao(pessoa);
  }

  @Test
  void conflitoDeHorarioBloqueiaCriacao() throws Exception {
    TenantContext.set(tenant);
    try {
      new TransactionTemplate(tm)
          .executeWithoutResult(
              tx -> em.find(EscalaVaga.class, vaga).getEvento().setHorario(LocalTime.of(19, 0)));
    } finally {
      TenantContext.clear();
    }
    mvc.perform(
            post("/portal/vagas/" + vaga + "/trocas")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("substitutoId", substituto, "versao", 0))))
        .andExpect(status().isConflict());
    alocacao(pessoa);
  }

  @Test
  void mudancaDeVinculoBloqueiaAprovacao() throws Exception {
    var p = pedir();
    assertThat(acao("/portal/trocas/" + id(p) + "/aceite", tokenB, true, 0)).isEqualTo(200);
    var u = vinculos.findByUsuario_IdAndTenant_Id(outroUsuario, tenant).orElseThrow();
    u.setPessoaId(null);
    vinculos.saveAndFlush(u);
    assertThat(acao("/escalas/trocas/" + id(p) + "/decisao", token, true, 1)).isEqualTo(409);
    alocacao(pessoa);
  }

  @Test
  void reabrirExpiraEHistoricoSobreviveExclusao() throws Exception {
    pedir();
    UUID escala = escalaId();
    TenantContext.set(tenant);
    try {
      escalaService.reabrir(escala);
      escalaService.finalizar(escala);
    } finally {
      TenantContext.clear();
    }
    mvc.perform(get("/portal/trocas").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.itens[0].situacao").value("EXPIRADA"));
    TenantContext.set(tenant);
    try {
      escalaService.cancelar(escala);
      escalaService.excluir(escala);
    } finally {
      TenantContext.clear();
    }
    mvc.perform(get("/portal/trocas").header("Authorization", "Bearer " + tokenB))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void diretorioMinimoNaoExpoeContatoENaoIncluiPessoaPropria() throws Exception {
    mvc.perform(
            get("/portal/trocas/substitutos")
                .param("busca", "Pessoa")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].nome").value("Pessoa B"))
        .andExpect(jsonPath("$[0].email").doesNotExist());
    mvc.perform(
            get("/portal/trocas/substitutos")
                .param("busca", "%")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/portal/trocas/substitutos")
                .param("busca", "%%")
                .header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void planoPermissaoDtoEDuplicidadeProtegidos() throws Exception {
    var p = pedir();
    mvc.perform(
            post("/portal/vagas/" + vaga + "/trocas")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("substitutoId", substituto, "versao", 0))))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/portal/vagas/" + vaga + "/trocas")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    plano();
    assertThat(acao("/portal/trocas/" + id(p) + "/aceite", tokenB, true, 0)).isEqualTo(403);
    mvc.perform(
            put("/portal/trocas/" + id(p) + "/cancelamento")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"versao\":0}"))
        .andExpect(status().isOk());
    var u = vinculos.findByUsuario_IdAndTenant_Id(usuario, tenant).orElseThrow();
    u.setRole(UsuarioTenant.Role.VISUALIZADOR);
    vinculos.saveAndFlush(u);
    mvc.perform(
            get("/portal/trocas/substitutos")
                .param("busca", "Pessoa")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void naoPodeSolicitarVagaDeOutraPessoaNemSubstituirPorSi() throws Exception {
    mvc.perform(
            post("/portal/vagas/" + vaga + "/trocas")
                .header("Authorization", "Bearer " + tokenB)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("substitutoId", pessoa, "versao", 0))))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/portal/vagas/" + vaga + "/trocas")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("substitutoId", pessoa, "versao", 0))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void outraParoquiaNaoLePedidoNemDiretorio() throws Exception {
    var p = pedir();
    var t =
        tenants.saveAndFlush(
            new Tenant(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "Externa",
                Tenant.Status.ATIVO));
    var a =
        new UsuarioTenant(
            usuarios.findById(usuario).orElseThrow(),
            t,
            UsuarioTenant.Role.ADMIN,
            UsuarioTenant.Status.ATIVO);
    vinculos.saveAndFlush(a);
    String externo = jwt.gerarAccessToken(usuario, t.getId(), UsuarioTenant.Role.ADMIN);
    assertThat(acao("/escalas/trocas/" + id(p) + "/decisao", externo, true, 0)).isIn(403, 404);
    mvc.perform(
            get("/escalas/" + escalaId() + "/trocas").header("Authorization", "Bearer " + externo))
        .andExpect(status().isNotFound());
  }
}
