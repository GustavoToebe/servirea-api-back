package br.com.servire.api.rodada;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.*;
import br.com.servire.api.calendario.*;
import br.com.servire.api.comunicacao.*;
import br.com.servire.api.escala.*;
import br.com.servire.api.evento.*;
import br.com.servire.api.integracao.*;
import br.com.servire.api.minhaconta.*;
import br.com.servire.api.mural.*;
import br.com.servire.api.pessoa.*;
import br.com.servire.api.security.JwtService;
import br.com.servire.api.site.*;
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
class Rodada1117HttpIntegrationTest extends AbstractIntegrationTest {
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
    paroquia.setCidade("Curitiba");
    paroquia.setUf("PR");
    tenants.saveAndFlush(paroquia);
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
    plano(
        "PORTAL_VOLUNTARIO",
        "CALENDARIO",
        "MURAL",
        "PASTORAIS",
        "ESCALAS",
        "TAREFAS",
        "COMUNICACAO",
        "LITURGIA",
        "ESTOQUE");
    token = jwt.gerarAccessToken(usuario, tenant, UsuarioTenant.Role.ADMIN);
    TenantContext.clear();
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
  @Autowired br.com.servire.api.estoque.EstoqueService estoque;

  org.springframework.test.web.servlet.request.RequestPostProcessor leitor(
      UUID id, String... perms) {
    TenantContext.set(tenant);
    var u = new br.com.servire.api.security.AuthenticatedUser(id, tenant, UsuarioTenant.Role.ADMIN);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            u,
            null,
            Arrays.stream(perms)
                .map(
                    x ->
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                            "PERM_" + x))
                .toList()));
  }

  tools.jackson.databind.JsonNode postJson(String rota, Object req) throws Exception {
    return json.readTree(
        mvc.perform(
                post(rota)
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  tools.jackson.databind.JsonNode putJson(String rota, Object req) throws Exception {
    return json.readTree(
        mvc.perform(
                put(rota)
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  Map<String, Object> referencia(long v, String url) {
    return Map.of(
        "versao",
        v,
        "titulo",
        "Referência da equipe",
        "fonte",
        "Fonte informada",
        "url",
        url,
        "ativo",
        true);
  }

  Map<String, Object> item(long v, String tipo) {
    return Map.of(
        "versao", v, "nome", "Velas", "codigo", "VELA", "tipo", tipo, "unidade", "unidade", "ativo",
        true);
  }

  Map<String, Object> movimento(long v, UUID chave, String tipo, String qtd) {
    return Map.of(
        "versao",
        v,
        "chave",
        chave,
        "tipo",
        tipo,
        "quantidade",
        qtd,
        "motivo",
        "Conferência manual");
  }

  void recusar(String rota, Object body, int statusEsperado) throws Exception {
    mvc.perform(
            post(rota)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().is(statusEsperado));
  }

  @Test
  void roteiroMantemSequenciaEConteudoExatamenteInformados() throws Exception {
    var ref = postJson("/liturgia/referencias", referencia(0, "https://example.test/referencia"));
    var passos =
        List.of(
            Map.of("titulo", "Primeiro", "referenciaId", ref.path("id").asString()),
            Map.of("titulo", "Segundo", "observacao", "Texto manual"));
    var r =
        postJson(
            "/liturgia/roteiros",
            Map.of(
                "versao",
                0,
                "titulo",
                "Roteiro manual",
                "celebracao",
                "Celebração informada",
                "passos",
                passos,
                "ativo",
                true));
    assertThat(r.path("passos").get(0).path("titulo").asString()).isEqualTo("Primeiro");
    assertThat(r.path("passos").get(1).path("observacao").asString()).isEqualTo("Texto manual");
    mvc.perform(get("/liturgia/roteiros").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void referenciaRecusaEsquemaExecutavelEUrlComCredenciais() throws Exception {
    recusar("/liturgia/referencias", referencia(0, "javascript:alert(1)"), 400);
    recusar("/liturgia/referencias", referencia(0, "https://usuario:senha@example.test"), 400);
  }

  @Test
  void roteiroRecusaReferenciaExternaENaoAceitaListaVazia() throws Exception {
    recusar(
        "/liturgia/roteiros",
        Map.of(
            "titulo",
            "R",
            "celebracao",
            "C",
            "passos",
            List.of(Map.of("titulo", "P", "referenciaId", UUID.randomUUID()))),
        400);
    recusar(
        "/liturgia/roteiros", Map.of("titulo", "R", "celebracao", "C", "passos", List.of()), 400);
  }

  @Test
  void referenciaTemConflitoDeVersao() throws Exception {
    var r = postJson("/liturgia/referencias", referencia(0, "https://example.test"));
    mvc.perform(
            put("/liturgia/referencias/" + r.path("id").asString())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(referencia(0, "https://example.test"))))
        .andExpect(status().isConflict());
  }

  @Test
  void lerLiturgiaNaoConcedeEdicao() throws Exception {
    mvc.perform(
            post("/liturgia/referencias")
                .with(leitor(usuario, "LITURGIA"))
                .contentType("application/json")
                .content(json.writeValueAsString(referencia(0, "https://example.test"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void planoSemModuloBloqueiaEscritaMasPreservaLeitura() throws Exception {
    plano("ESCALAS");
    recusar("/liturgia/referencias", referencia(0, "https://example.test"), 403);
    recusar("/estoque", item(0, "CONSUMIVEL"), 403);
    mvc.perform(get("/liturgia/referencias").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void indicadoresContamSomenteEscalasFinalizadasNaoReferencia() throws Exception {
    var s =
        mvc.perform(
                get("/indicadores/participacao")
                    .param("de", dia.toString())
                    .param("ate", dia.toString())
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.resumo.alocacoes").value(2))
            .andExpect(jsonPath("$.itens[0].nome").value("Pessoa A"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(s).doesNotContain("email", "telefone", "ranking", "@teste.test");
  }

  @Test
  void indicadoresSemAmbasPermissoesSaoPrivados() throws Exception {
    mvc.perform(
            get("/indicadores/participacao")
                .param("de", dia.toString())
                .param("ate", dia.toString())
                .with(leitor(usuario, "ESCALA")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/indicadores/participacao")
                .param("de", dia.toString())
                .param("ate", dia.toString())
                .with(leitor(usuario, "INDICADORES")))
        .andExpect(status().isForbidden());
  }

  @Test
  void indicadoresValidamPeriodoEEscapamCuringa() throws Exception {
    mvc.perform(
            get("/indicadores/participacao")
                .param("de", dia.toString())
                .param("ate", dia.plusDays(400).toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/indicadores/participacao")
                .param("de", dia.toString())
                .param("ate", dia.toString())
                .param("busca", "%")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void movimentoAtualizaSaldoERepeticaoNaoDuplica() throws Exception {
    var e = postJson("/estoque", item(0, "CONSUMIVEL"));
    String id = e.path("id").asString();
    var req = movimento(e.path("versao").asLong(), UUID.randomUUID(), "ENTRADA", "10.125");
    var a = postJson("/estoque/" + id + "/movimentos", req);
    var b = postJson("/estoque/" + id + "/movimentos", req);
    assertThat(b.path("id").asString()).isEqualTo(a.path("id").asString());
    mvc.perform(get("/estoque/" + id).header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.saldo").value(10.125));
    mvc.perform(get("/estoque/" + id + "/movimentos").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void estoqueRecusaSaldoNegativoEPatrimonioFracionado() throws Exception {
    var e = postJson("/estoque", item(0, "PATRIMONIO"));
    String rota = "/estoque/" + e.path("id").asString() + "/movimentos";
    recusar(rota, movimento(1, UUID.randomUUID(), "SAIDA", "1"), 400);
    recusar(rota, movimento(1, UUID.randomUUID(), "ENTRADA", "0.5"), 400);
  }

  @Test
  void chaveReutilizadaComOutroConteudoRetornaConflito() throws Exception {
    var e = postJson("/estoque", item(0, "CONSUMIVEL"));
    String rota = "/estoque/" + e.path("id").asString() + "/movimentos";
    UUID chave = UUID.randomUUID();
    postJson(rota, movimento(1, chave, "ENTRADA", "2"));
    recusar(rota, movimento(1, chave, "ENTRADA", "3"), 409);
    recusar(rota, movimento(1, UUID.randomUUID(), "ENTRADA", "3"), 409);
  }

  @Test
  void ajusteSemAlterarSaldoAvancaVersao() throws Exception {
    var e = postJson("/estoque", item(0, "CONSUMIVEL"));
    String id = e.path("id").asString();
    postJson("/estoque/" + id + "/movimentos", movimento(1, UUID.randomUUID(), "AJUSTE", "0"));
    mvc.perform(get("/estoque/" + id).header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.versao").value(2));
  }

  @Test
  void ajusteExigePermissaoPropria() throws Exception {
    var e = postJson("/estoque", item(0, "CONSUMIVEL"));
    mvc.perform(
            post("/estoque/" + e.path("id").asString() + "/movimentos")
                .with(leitor(usuario, "ESTOQUE", "ESTOQUE_MOVIMENTAR"))
                .contentType("application/json")
                .content(json.writeValueAsString(movimento(1, UUID.randomUUID(), "AJUSTE", "0"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void responsavelExternoETipoAposMovimentoSaoRecusados() throws Exception {
    var req = new HashMap<>(item(0, "CONSUMIVEL"));
    req.put("responsavelUsuarioId", UUID.randomUUID());
    recusar("/estoque", req, 400);
    var e = postJson("/estoque", item(0, "CONSUMIVEL"));
    String id = e.path("id").asString();
    postJson("/estoque/" + id + "/movimentos", movimento(1, UUID.randomUUID(), "ENTRADA", "2"));
    mvc.perform(
            put("/estoque/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(item(2, "PATRIMONIO"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void itemNaoPermiteSobrescreverSaldoNoCadastro() throws Exception {
    var req = new HashMap<>(item(0, "CONSUMIVEL"));
    req.put("saldo", 100);
    var e = postJson("/estoque", req);
    assertThat(e.path("saldo").asDouble()).isZero();
  }

  @Test
  void duasSaidasSimultaneasNaoConsomemOMesmoSaldo() throws Exception {
    var e = postJson("/estoque", item(0, "CONSUMIVEL"));
    UUID id = UUID.fromString(e.path("id").asString());
    postJson("/estoque/" + id + "/movimentos", movimento(1, UUID.randomUUID(), "ENTRADA", "1"));
    var inicio = new java.util.concurrent.CountDownLatch(1);
    try (var exec = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var tarefas = new ArrayList<java.util.concurrent.Future<Boolean>>();
      for (int i = 0; i < 2; i++)
        tarefas.add(
            exec.submit(
                () -> {
                  inicio.await();
                  TenantContext.set(tenant);
                  try {
                    estoque.movimentar(
                        id,
                        new br.com.servire.api.estoque.dto.EstoqueDtos.Movimentar(
                            2,
                            UUID.randomUUID(),
                            "SAIDA",
                            java.math.BigDecimal.ONE,
                            "Saída concorrente",
                            null));
                    return true;
                  } catch (br.com.servire.api.web.ConflictException ex) {
                    return false;
                  } finally {
                    TenantContext.clear();
                  }
                }));
      inicio.countDown();
      int aprovados = 0;
      for (var f : tarefas) if (f.get(20, java.util.concurrent.TimeUnit.SECONDS)) aprovados++;
      assertThat(aprovados).isEqualTo(1);
    }
    mvc.perform(get("/estoque/" + id).header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.saldo").value(0));
  }

  @Test
  void seletoresProjetamDadosMinimosEPaginam() throws Exception {
    TenantContext.set(tenant);
    new TransactionTemplate(tm)
        .executeWithoutResult(
            tx -> {
              for (int i = 0; i < 32; i++) pessoa(String.format("Opção %02d", i));
            });
    TenantContext.clear();
    var s =
        mvc.perform(
                get("/voluntarios/opcoes")
                    .param("nome", "Opção")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itens.length()").value(30))
            .andExpect(jsonPath("$.temMais").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(s).doesNotContain("foto", "dataNascimento", "email", "telefone", "observacoes");
    mvc.perform(
            get("/voluntarios/opcoes")
                .param("nome", "Opção")
                .param("pagina", "1")
                .header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.itens.length()").value(2))
        .andExpect(jsonPath("$.temMais").value(false));
  }

  @Test
  void seletoresLiteralESemIdsArbitrarios() throws Exception {
    mvc.perform(
            get("/voluntarios/opcoes")
                .param("nome", "%")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.itens.length()").value(0));
    var r = postJson("/voluntarios/opcoes/ids", Map.of("ids", List.of(pessoa, UUID.randomUUID())));
    assertThat(r.size()).isEqualTo(1);
    recusar(
        "/voluntarios/opcoes/ids",
        Map.of(
            "ids",
            java.util.stream.IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID()).toList()),
        400);
  }

  @Test
  void outraParoquiaNaoVeEstoqueLiturgiaIndicadoresOuOpcoes() throws Exception {
    var e = postJson("/estoque", item(0, "CONSUMIVEL"));
    postJson("/liturgia/referencias", referencia(0, "https://example.test"));
    String s = UUID.randomUUID().toString();
    var t = tenants.saveAndFlush(new Tenant(s, s, "Outra", Tenant.Status.ATIVO));
    var u = usuarios.saveAndFlush(new Usuario(s + "@teste.test", "Externo"));
    vinculos.saveAndFlush(
        new UsuarioTenant(u, t, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO));
    String externo = jwt.gerarAccessToken(u.getId(), t.getId(), UsuarioTenant.Role.ADMIN);
    mvc.perform(
            get("/estoque/" + e.path("id").asString()).header("Authorization", "Bearer " + externo))
        .andExpect(status().isNotFound());
    mvc.perform(get("/liturgia/referencias").header("Authorization", "Bearer " + externo))
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(
            get("/indicadores/participacao")
                .param("de", dia.toString())
                .param("ate", dia.toString())
                .header("Authorization", "Bearer " + externo))
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(
            post("/voluntarios/opcoes/ids")
                .header("Authorization", "Bearer " + externo)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("ids", List.of(pessoa)))))
        .andExpect(jsonPath("$.length()").value(0));
  }
}
