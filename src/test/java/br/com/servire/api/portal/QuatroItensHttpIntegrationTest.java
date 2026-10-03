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
class QuatroItensHttpIntegrationTest extends AbstractIntegrationTest {
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
    plano("PORTAL_VOLUNTARIO", "CALENDARIO", "MURAL", "PASTORAIS", "ESCALAS", "TAREFAS");
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

  String mesRota() {
    return "/portal/indisponibilidades?ano=" + dia.getYear() + "&mes=" + dia.getMonthValue();
  }

  String adminMes() {
    return "/escalas/indisponibilidades?ano=" + dia.getYear() + "&mes=" + dia.getMonthValue();
  }

  String relatorio() {
    return "/relatorios/participacao?de=" + dia + "&ate=" + dia;
  }

  String csv() {
    return "/relatorios/participacao/csv?de=" + dia + "&ate=" + dia;
  }

  String bearerB() {
    return jwt.gerarAccessToken(outroUsuario, tenant, UsuarioTenant.Role.ADMIN);
  }

  String restricao(long versao) {
    return json.writeValueAsString(
        Map.of(
            "versao",
            versao,
            "semRestricao",
            false,
            "itens",
            List.of(Map.of("data", dia.toString(), "periodo", "MANHA"))));
  }

  @Test
  void respostaPropriaNaoSobrescreveOutraPessoaECoordenaVersao() throws Exception {
    mvc.perform(
            put(mesRota())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(restricao(0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versao").value(1));
    mvc.perform(get(mesRota()).header("Authorization", "Bearer " + bearerB()))
        .andExpect(jsonPath("$.itens.length()").value(0))
        .andExpect(jsonPath("$.versao").value(1));
    mvc.perform(
            put(mesRota())
                .header("Authorization", "Bearer " + bearerB())
                .contentType("application/json")
                .content("{\"versao\":1,\"semRestricao\":true,\"itens\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versao").value(2));
    mvc.perform(get(adminMes()).header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.itens.length()").value(1))
        .andExpect(jsonPath("$.semRestricao.length()").value(1));
    mvc.perform(
            put(adminMes())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"versao\":0,\"itens\":[],\"semRestricao\":[]}"))
        .andExpect(status().isConflict());
    mvc.perform(get(mesRota()).header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.itens.length()").value(1));
  }

  @Test
  void disputaPorMesNaoPerdeResposta() throws Exception {
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () ->
                  mvc.perform(
                          put(mesRota())
                              .header("Authorization", "Bearer " + token)
                              .contentType("application/json")
                              .content(restricao(0)))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      var b =
          pool.submit(
              () ->
                  mvc.perform(
                          put(mesRota())
                              .header("Authorization", "Bearer " + bearerB())
                              .contentType("application/json")
                              .content(restricao(0)))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      assertThat(
              List.of(
                  a.get(20, java.util.concurrent.TimeUnit.SECONDS),
                  b.get(20, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
  }

  @Test
  void indisponibilidadeValidaDadosPlanoEVinculo() throws Exception {
    mvc.perform(
            put(mesRota())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"semRestricao\":true,\"itens\":[]}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put(mesRota())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "versao",
                            0,
                            "semRestricao",
                            true,
                            "itens",
                            List.of(Map.of("data", dia.toString()))))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put(mesRota())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "versao",
                            0,
                            "semRestricao",
                            false,
                            "itens",
                            List.of(
                                Map.of("data", dia.toString()),
                                Map.of("data", dia.toString(), "periodo", "NOITE"))))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put(mesRota())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "versao",
                            0,
                            "semRestricao",
                            false,
                            "itens",
                            List.of(Map.of("data", dia.plusMonths(1).toString()))))))
        .andExpect(status().isBadRequest());
    plano();
    mvc.perform(get(mesRota()).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    plano("PORTAL_VOLUNTARIO");
    var v = vinculos.findByUsuario_IdAndTenant_Id(usuario, tenant).orElseThrow();
    v.setPessoaId(null);
    vinculos.saveAndFlush(v);
    mvc.perform(
            put(mesRota())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(restricao(0)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void restricaoDoPortalAlimentaElegibilidadeSemAlterarVagas() throws Exception {
    var body =
        json.writeValueAsString(
            Map.of(
                "versao",
                0,
                "semRestricao",
                false,
                "itens",
                List.of(Map.of("data", dia.toString()))));
    mvc.perform(
            put(mesRota())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk());
    TenantContext.set(tenant);
    try {
      new TransactionTemplate(tm)
          .executeWithoutResult(
              tx -> {
                assertThat(
                        em.createQuery(
                                "select count(i) from Indisponibilidade i where i.voluntarioId=:p",
                                Long.class)
                            .setParameter("p", pessoa)
                            .getSingleResult())
                    .isEqualTo(1);
                assertThat(
                        em.createQuery(
                                "select count(v) from EscalaVaga v where v.voluntario.id=:p",
                                Long.class)
                            .setParameter("p", pessoa)
                            .getSingleResult())
                    .isEqualTo(3);
              });
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void tarefaAtribuiFiltraLimpaERejeitaContaExterna() throws Exception {
    var n =
        json.readTree(
            mvc.perform(
                    post("/tarefas")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(tarefa(outroUsuario, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsavelNome").value("Conta B"))
                .andReturn()
                .getResponse()
                .getContentAsString());
    mvc.perform(get("/tarefas").param("minhas", "true").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(
            get("/tarefas").param("minhas", "true").header("Authorization", "Bearer " + bearerB()))
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(
            get("/tarefas")
                .param("responsavelUsuarioId", outroUsuario.toString())
                .param("status", "ABERTA")
                .header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(
            post("/tarefas")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(tarefa(UUID.randomUUID(), 0)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put("/tarefas/" + n.path("id").asString())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(tarefa(null, 0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.responsavelUsuarioId").isEmpty());
  }

  String tarefa(UUID id, long versao) {
    var m = new HashMap<String, Object>();
    m.put("titulo", "Atividade");
    m.put("descricao", "Descrição");
    m.put("status", "ABERTA");
    m.put("versao", versao);
    m.put("responsavelUsuarioId", id);
    return json.writeValueAsString(m);
  }

  @Test
  void diretorioDeTarefasSoExpoeVinculosAtivosDaParoquia() throws Exception {
    var externo = usuarios.saveAndFlush(new Usuario(UUID.randomUUID() + "@teste.test", "Externo"));
    mvc.perform(get("/tarefas/responsaveis").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].email").doesNotExist());
    mvc.perform(
            post("/tarefas")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(tarefa(externo.getId(), 0)))
        .andExpect(status().isBadRequest());
    var v = vinculos.findByUsuario_IdAndTenant_Id(outroUsuario, tenant).orElseThrow();
    v.setStatus(UsuarioTenant.Status.INATIVO);
    vinculos.saveAndFlush(v);
    mvc.perform(
            post("/tarefas")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(tarefa(outroUsuario, 0)))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/tarefas/responsaveis").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void relatorioFiltraNoServidorSemRascunhosReferenciasOuDadosPessoais() throws Exception {
    mvc.perform(get(relatorio()).header("Authorization", "Bearer " + token).param("tamanho", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.itens.length()").value(1))
        .andExpect(jsonPath("$.itens[0].cpf").doesNotExist())
        .andExpect(jsonPath("$.itens[0].email").doesNotExist());
    mvc.perform(
            get(relatorio())
                .header("Authorization", "Bearer " + token)
                .param("busca", "Pessoa A")
                .param("presenca", "PENDENTE")
                .param("resposta", "PENDENTE"))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.itens[0].pessoa").value("Pessoa A"));
    mvc.perform(
            get(relatorio()).header("Authorization", "Bearer " + token).param("presenca", "FALTOU"))
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get(relatorio()).header("Authorization", "Bearer " + token).param("busca", "%"))
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void csvEscapaFormulaSeparadoresEQuebras() throws Exception {
    TenantContext.set(tenant);
    try {
      new TransactionTemplate(tm)
          .executeWithoutResult(
              tx -> {
                pessoas.findById(pessoa).orElseThrow().setNomeCompleto("  =SOMA(1;2)\r\n\"texto\"");
              });
    } finally {
      TenantContext.clear();
    }
    var r =
        mvc.perform(get(csv()).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(
                header().string("Content-Disposition", "attachment; filename=participacao.csv"))
            .andReturn()
            .getResponse();
    String s = new String(r.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(s)
        .startsWith("\uFEFFEscala;")
        .contains("\"'  =SOMA(1;2)\r\n\"\"texto\"\"\"")
        .doesNotContain("Rascunho privado", "Linha de referência");
  }

  @Test
  void exportacaoExigePermissaoPropria() throws Exception {
    var v = vinculos.findByUsuario_IdAndTenant_Id(usuario, tenant).orElseThrow();
    v.setRole(UsuarioTenant.Role.VISUALIZADOR);
    vinculos.saveAndFlush(v);
    mvc.perform(get(csv()).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void outroTenantNaoVeRelatorioNemRespostaOriginal() throws Exception {
    mvc.perform(
            put(mesRota())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(restricao(0)))
        .andExpect(status().isOk());
    var t =
        tenants.saveAndFlush(
            new Tenant(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "Externa",
                Tenant.Status.ATIVO));
    var v =
        new UsuarioTenant(
            usuarios.findById(usuario).orElseThrow(),
            t,
            UsuarioTenant.Role.ADMIN,
            UsuarioTenant.Status.ATIVO);
    vinculos.saveAndFlush(v);
    String outro = jwt.gerarAccessToken(usuario, t.getId(), UsuarioTenant.Role.ADMIN);
    mvc.perform(get(relatorio()).header("Authorization", "Bearer " + outro))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get(mesRota()).header("Authorization", "Bearer " + outro))
        .andExpect(status().isForbidden());
  }

  @Test
  void reportRejeitaPeriodoLongoEPaginacaoInvalida() throws Exception {
    mvc.perform(
            get("/relatorios/participacao")
                .param("de", dia.toString())
                .param("ate", dia.plusDays(367).toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    mvc.perform(get(relatorio()).param("tamanho", "101").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
  }

  @Test
  void leituraNaoAutorizaExportarNemInformarDatas() throws Exception {
    TenantContext.set(tenant);
    mvc.perform(
            get(csv())
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.user("leitor")
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "PERM_AUDITORIA"))))
        .andExpect(status().isForbidden());
    TenantContext.set(tenant);
    mvc.perform(
            put(mesRota())
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.user("leitor")
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "PERM_PORTAL_VOLUNTARIO")))
                .contentType("application/json")
                .content(restricao(0)))
        .andExpect(status().isForbidden());
  }
}
