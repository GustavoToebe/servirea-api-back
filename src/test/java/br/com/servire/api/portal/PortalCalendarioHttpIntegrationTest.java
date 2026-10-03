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
class PortalCalendarioHttpIntegrationTest extends AbstractIntegrationTest {
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
    plano("PORTAL_VOLUNTARIO", "CALENDARIO", "MURAL", "PASTORAIS");
    token = jwt.gerarAccessToken(usuario, tenant, UsuarioTenant.Role.ADMIN);
    TenantContext.clear();
  }

  Pessoa pessoa(String nome) {
    var p = pessoas.saveAndFlush(new Pessoa(PessoaPapel.VOLUNTARIO, nome));
    var v = new Voluntario();
    v.setPessoa(p);
    v.setTipo(TipoVoluntario.COROINHA);
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

  @Test
  void jwtPessoalMostraSomenteCompromissosPublicadosDaPropriaPessoa() throws Exception {
    mvc.perform(
            get("/portal/compromissos")
                .param("de", dia.toString())
                .param("ate", dia.toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vinculado").value(true))
        .andExpect(jsonPath("$.compromissos.length()").value(2))
        .andExpect(jsonPath("$.compromissos[0].titulo").value("Missa A"))
        .andExpect(jsonPath("$.compromissos[1].titulo").value("Evento A"));
  }

  @Test
  void semVinculoNaoInferimosPessoaPorEmailOuNome() throws Exception {
    var v = vinculos.findByUsuario_IdAndTenant_Id(usuario, tenant).orElseThrow();
    v.setPessoaId(null);
    vinculos.saveAndFlush(v);
    mvc.perform(
            get("/portal/compromissos")
                .param("de", dia.toString())
                .param("ate", dia.toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vinculado").value(false))
        .andExpect(jsonPath("$.compromissos").isEmpty());
  }

  @Test
  void calendarioPrivadoNaoIncluiOutrasPessoasETokenFicaSomenteEmHash() throws Exception {
    String segredo = assinatura();
    assertThat(calendarios.findByTenantIdAndUsuarioId(tenant, usuario).orElseThrow().tokenHash)
        .doesNotContain(segredo);
    var r =
        mvc.perform(get("/public/calendario/" + segredo + ".ics"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(r)
        .contains("SUMMARY:Missa A", "SUMMARY:Evento A", "DTSTART:", "CLASS:PRIVATE")
        .doesNotContain("Missa B", "Evento B", "Rascunho");
  }

  @Test
  void rotacaoRevogacaoEVinculoInativoInvalidamLink() throws Exception {
    String velho = assinatura();
    String novo = assinatura();
    mvc.perform(get("/public/calendario/" + velho + ".ics")).andExpect(status().isNotFound());
    mvc.perform(get("/public/calendario/" + novo + ".ics")).andExpect(status().isOk());
    var v = vinculos.findByUsuario_IdAndTenant_Id(usuario, tenant).orElseThrow();
    v.setStatus(UsuarioTenant.Status.INATIVO);
    vinculos.saveAndFlush(v);
    mvc.perform(get("/public/calendario/" + novo + ".ics")).andExpect(status().isNotFound());
    v.setStatus(UsuarioTenant.Status.ATIVO);
    vinculos.saveAndFlush(v);
    mvc.perform(delete("/calendario/assinatura").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(get("/public/calendario/" + novo + ".ics")).andExpect(status().isNotFound());
  }

  @Test
  void downgradeBloqueiaPortalEFeedMasPermiteRevogar() throws Exception {
    String segredo = assinatura();
    plano();
    mvc.perform(
            get("/portal/compromissos")
                .param("de", dia.toString())
                .param("ate", dia.toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/public/calendario/" + segredo + ".ics")).andExpect(status().isNotFound());
    mvc.perform(delete("/calendario/assinatura").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void administradorNaoAssociaPessoaJaVinculadaOuDeOutraParoquia() throws Exception {
    mvc.perform(
            put("/usuarios/" + outroUsuario + "/pessoa")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"pessoaId\":\"" + pessoa + "\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/usuarios/" + usuario + "/pessoa")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"pessoaId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void removerVinculoRevogaAssinatura() throws Exception {
    String segredo = assinatura();
    mvc.perform(
            put("/usuarios/" + usuario + "/pessoa")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"pessoaId\":null}"))
        .andExpect(status().isOk());
    mvc.perform(get("/public/calendario/" + segredo + ".ics")).andExpect(status().isNotFound());
  }

  @Test
  void contaSemPessoaVinculadaRecebe403NaCoordenacaoEnaoErro500() throws Exception {
    mvc.perform(
            put("/usuarios/" + usuario + "/pessoa")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"pessoaId\":null}"))
        .andExpect(status().isOk());
    mvc.perform(get("/pastorais/minhas-equipes").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void expiracaoETokenInvalidoNaoLiberamCalendario() throws Exception {
    String segredo = assinatura();
    var c = calendarios.findByTenantIdAndUsuarioId(tenant, usuario).orElseThrow();
    c.expiraEm = Instant.now().minusSeconds(1);
    calendarios.saveAndFlush(c);
    mvc.perform(get("/public/calendario/" + segredo + ".ics")).andExpect(status().isNotFound());
    mvc.perform(get("/public/calendario/invalido.ics")).andExpect(status().isNotFound());
  }

  @Test
  void perdaDePermissaoInvalidaFeedMesmoComPlanoContratado() throws Exception {
    String segredo = assinatura();
    var v = vinculos.findByUsuario_IdAndTenant_Id(usuario, tenant).orElseThrow();
    v.setRole(UsuarioTenant.Role.VISUALIZADOR);
    vinculos.saveAndFlush(v);
    mvc.perform(get("/public/calendario/" + segredo + ".ics")).andExpect(status().isNotFound());
    mvc.perform(
            get("/portal/compromissos")
                .param("de", dia.toString())
                .param("ate", dia.toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void homologacaoDePlanoRealMantemHistoricoEBloqueiaMutacaoNaoContratada() throws Exception {
    mvc.perform(
            post("/financeiro/contas")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("FUNCIONALIDADE_NAO_CONTRATADA"));
    mvc.perform(get("/financeiro/contas").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(
            post("/mural/avisos")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    "{\"titulo\":\"Homologação\",\"descricao\":\"Aviso\",\"status\":\"PUBLICADO\",\"prazo\":null,\"versao\":null}"))
        .andExpect(status().isOk());
  }

  @Autowired jakarta.persistence.EntityManager em;

  UUID dependente() {
    return vinculos.findByUsuario_IdAndTenant_Id(outroUsuario, tenant).orElseThrow().getPessoaId();
  }

  void parentesco() {
    TenantContext.set(tenant);
    new TransactionTemplate(tm)
        .executeWithoutResult(
            tx ->
                em.persist(
                    new PessoaRelacao(
                        pessoas.findById(pessoa).orElseThrow(),
                        pessoas.findById(dependente()).orElseThrow(),
                        "Mãe",
                        "Filho",
                        true)));
    TenantContext.clear();
  }

  void autorizar(boolean consulta, boolean resposta, long versao, int status) throws Exception {
    mvc.perform(
            put("/pessoas/" + pessoa + "/acessos-dependentes/" + dependente())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("consulta", consulta, "resposta", resposta, "versao", versao))))
        .andExpect(status().is(status));
  }

  String compromissosDependente() throws Exception {
    return mvc.perform(
            get("/portal/dependentes/" + dependente() + "/compromissos")
                .param("de", dia.toString())
                .param("ate", dia.toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.compromissos.length()").value(2))
        .andExpect(jsonPath("$.compromissos[0].titulo").value("Missa B"))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void parentescoSoNaoAutorizaEConsultaPodeSerRevogada() throws Exception {
    parentesco();
    mvc.perform(get("/portal/dependentes").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mvc.perform(
            get("/portal/dependentes/" + dependente() + "/compromissos")
                .param("de", dia.toString())
                .param("ate", dia.toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    autorizar(true, false, 0, 200);
    compromissosDependente();
    mvc.perform(get("/portal/dependentes").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].podeResponder").value(false));
    autorizar(false, false, 1, 200);
    mvc.perform(
            get("/portal/dependentes/" + dependente() + "/compromissos")
                .param("de", dia.toString())
                .param("ate", dia.toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void respostaExigeAutorizacaoEspecificaEVersaoERegistraResponsavel() throws Exception {
    parentesco();
    autorizar(true, false, 0, 200);
    String vaga =
        json.readTree(compromissosDependente())
            .path("compromissos")
            .get(0)
            .path("vagaId")
            .asString();
    String url = "/portal/dependentes/" + dependente() + "/vagas/" + vaga + "/resposta";
    String corpo = "{\"resposta\":\"CONFIRMADA\",\"versao\":0}";
    mvc.perform(
            put(url)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(corpo))
        .andExpect(status().isNotFound());
    autorizar(true, true, 1, 200);
    mvc.perform(
            put(url)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(corpo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resposta").value("CONFIRMADA"));
    mvc.perform(
            put(url)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"resposta\":\"RECUSADA\",\"versao\":0}"))
        .andExpect(status().isConflict());
    TenantContext.set(tenant);
    new TransactionTemplate(tm)
        .executeWithoutResult(
            tx -> {
              var h =
                  em.createQuery(
                          "select h from RespostaHistorico h where h.vagaId=:v",
                          RespostaHistorico.class)
                      .setParameter("v", UUID.fromString(vaga))
                      .getSingleResult();
              assertThat(h.usuarioId).isEqualTo(usuario);
              assertThat(h.pessoaId).isEqualTo(dependente());
            });
    TenantContext.clear();
    autorizar(false, false, 2, 200);
    mvc.perform(
            put(url)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(corpo))
        .andExpect(status().isNotFound());
  }

  @Test
  void autorizacaoNaoAceitaVersaoAntigaNemRespostaSemConsulta() throws Exception {
    parentesco();
    autorizar(false, true, 0, 400);
    autorizar(true, false, 0, 200);
    autorizar(false, false, 0, 409);
    mvc.perform(
            put("/pessoas/" + pessoa + "/acessos-dependentes/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"consulta\":true,\"resposta\":false,\"versao\":0}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void responsavelNaoAcessaDependenteDeOutraParoquiaNemFichaCompleta() throws Exception {
    parentesco();
    autorizar(true, false, 0, 200);
    var vinculo = vinculos.findByUsuario_IdAndTenant_Id(usuario, tenant).orElseThrow();
    vinculo.setRole(UsuarioTenant.Role.VISUALIZADOR);
    vinculos.saveAndFlush(vinculo);
    mvc.perform(get("/portal/dependentes").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/portal/dependentes/" + UUID.randomUUID() + "/compromissos")
                .param("de", dia.toString())
                .param("ate", dia.toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  String equipePropria(String nome) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/pastorais/equipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("nome", nome, "ativo", true))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .path("id")
        .asString();
  }

  void participante(String equipe, UUID p, String papel, boolean ativo, Long versao)
      throws Exception {
    var req = new HashMap<String, Object>();
    req.put("pessoaId", p);
    req.put("papel", papel);
    req.put("ativo", ativo);
    req.put("versao", versao);
    mvc.perform(
            post("/pastorais/equipes/" + equipe + "/membros")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(req)))
        .andExpect(status().isOk());
  }

  @Test
  void coordenacaoSoConsultaEquipePropriaEVinculoRevogadoBloqueia() throws Exception {
    String propria = equipePropria("Minha equipe"), outra = equipePropria("Outra equipe");
    participante(propria, pessoa, "COORDENADOR", true, null);
    participante(outra, dependente(), "COORDENADOR", true, null);
    mvc.perform(get("/pastorais/minhas-equipes").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(propria));
    mvc.perform(
            get("/pastorais/minhas-equipes/" + outra + "/membros")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    participante(propria, pessoa, "COORDENADOR", false, 0L);
    mvc.perform(
            get("/pastorais/minhas-equipes/" + propria + "/membros")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void coordenacaoNaoPromoveCoordenadoresNemSobrescreveMembro() throws Exception {
    String equipe = equipePropria("Equipe limitada");
    participante(equipe, pessoa, "COORDENADOR", true, null);
    participante(equipe, dependente(), "MEMBRO", true, null);
    String prefixo = "/pastorais/minhas-equipes/" + equipe + "/membros/";
    mvc.perform(
            put(prefixo + pessoa)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"ativo\":false,\"versao\":0}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put(prefixo + dependente())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"ativo\":false,\"versao\":0}"))
        .andExpect(status().isOk());
    mvc.perform(
            put(prefixo + dependente())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"ativo\":true,\"versao\":0}"))
        .andExpect(status().isConflict());
    mvc.perform(
            put(prefixo + UUID.randomUUID())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"ativo\":true,\"versao\":0}"))
        .andExpect(status().isNotFound());
  }
}
