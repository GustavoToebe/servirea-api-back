package br.com.servire.api.rodada;
import br.com.servire.api.comunicacao.*;
import br.com.servire.api.mural.*;
import br.com.servire.api.minhaconta.*;
import br.com.servire.api.site.*;
import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.*;
import br.com.servire.api.tenant.*;
import br.com.servire.api.pessoa.*;
import br.com.servire.api.voluntario.*;
import br.com.servire.api.escala.*;
import br.com.servire.api.evento.*;
import br.com.servire.api.integracao.*;
import br.com.servire.api.security.JwtService;
import br.com.servire.api.calendario.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@AutoConfigureMockMvc
class Rodada610HttpIntegrationTest extends AbstractIntegrationTest {
 @Autowired MockMvc mvc;@Autowired TenantRepository tenants;@Autowired UsuarioRepository usuarios;@Autowired UsuarioTenantRepository vinculos;
 @Autowired PessoaRepository pessoas;@Autowired VoluntarioRepository voluntarios;@Autowired EscalaRepository escalas;@Autowired EventoRepository eventos;@Autowired EventoInscricaoRepository inscritos;
 @Autowired DireitosLocaisRepository direitos;@Autowired JwtService jwt;@Autowired JsonMapper json;@Autowired CalendarioRepository calendarios;@Autowired PlatformTransactionManager tm;
 @Autowired EscalaService escalaService;
 UUID tenant,usuario,pessoa,outroUsuario;String token;LocalDate dia;
 @BeforeEach void preparar(){doCallRealMethod().when(funcionalidadesPlano).liberadas();String slug=UUID.randomUUID().toString();var paroquia=tenants.saveAndFlush(new Tenant(slug,slug,"Portal",Tenant.Status.ATIVO));paroquia.setCidade("Curitiba");paroquia.setUf("PR");tenants.saveAndFlush(paroquia);tenant=paroquia.getId();TenantContext.set(tenant);dia=LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(10);
  var u=usuarios.saveAndFlush(new Usuario(slug+"@teste.test","Conta A"));usuario=u.getId();var v=usuarios.saveAndFlush(new Usuario("b-"+slug+"@teste.test","Conta B"));outroUsuario=v.getId();
  new TransactionTemplate(tm).executeWithoutResult(tx->{var a=pessoa("Pessoa A");var b=pessoa("Pessoa B");pessoa=a.getId();var va=new UsuarioTenant(u,paroquia,UsuarioTenant.Role.ADMIN,UsuarioTenant.Status.ATIVO);va.setPessoaId(a.getId());vinculos.saveAndFlush(va);var vb=new UsuarioTenant(v,paroquia,UsuarioTenant.Role.ADMIN,UsuarioTenant.Status.ATIVO);vb.setPessoaId(b.getId());vinculos.saveAndFlush(vb);
   escala(a,StatusEscala.FINALIZADA,false,"Missa A");escala(b,StatusEscala.FINALIZADA,false,"Missa B");escala(a,StatusEscala.RASCUNHO,false,"Rascunho privado");escala(a,StatusEscala.FINALIZADA,true,"Linha de referência");
   evento(a,"Evento A",Evento.Situacao.PUBLICADO);evento(b,"Evento B",Evento.Situacao.PUBLICADO);evento(a,"Rascunho evento",Evento.Situacao.RASCUNHO);
  });plano("PORTAL_VOLUNTARIO","CALENDARIO","MURAL","PASTORAIS","ESCALAS","TAREFAS","COMUNICACAO");token=jwt.gerarAccessToken(usuario,tenant,UsuarioTenant.Role.ADMIN);TenantContext.clear();
 }
 Pessoa pessoa(String nome){var p=pessoas.saveAndFlush(new Pessoa(PessoaPapel.VOLUNTARIO,nome));var v=new Voluntario();v.setPessoa(p);v.setTipo(TipoVoluntario.COROINHA);v.setFuncoesHabilitadas(new FuncaoEscala[]{FuncaoEscala.MISSAL});voluntarios.saveAndFlush(v);return p;}
 void escala(Pessoa p,StatusEscala status,boolean referencia,String titulo){var s=new Escala(titulo,TipoEscala.MENSAL);s.setStatus(status);var e=new EscalaEvento(dia,LocalTime.of(19,0),titulo);e.setEscala(s);e.setReferencia(referencia);s.getEventos().add(e);var vaga=new EscalaVaga(FuncaoEscala.values()[0],1);vaga.setEvento(e);vaga.setVoluntario(voluntarios.findById(p.getId()).orElseThrow());e.getVagas().add(vaga);escalas.saveAndFlush(s);}
 void evento(Pessoa p,String titulo,Evento.Situacao status){var e=new Evento(titulo,dia.atTime(20,0));e.setSituacao(status);eventos.saveAndFlush(e);inscritos.saveAndFlush(new EventoInscricao(e.getId(),p,false));}
 void plano(String...codigos){var d=direitos.findById(tenant).orElseGet(()->new DireitosLocais(tenant,UUID.randomUUID()));d.setSituacao("ATIVA");d.setAcessoLiberado(true);d.setConfirmadoEm(Instant.now());d.setFuncionalidades(codigos);direitos.saveAndFlush(d);}
 String assinatura()throws Exception {return json.readTree(mvc.perform(post("/calendario/assinatura").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asString();}
 @AfterEach void limpar(){TenantContext.clear();}



 @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;


 @Autowired AniversarioService aniversarioService;@Autowired ConsumoHistoricoService consumoHistorico;@Autowired CotasService cotas;@Autowired FilaDeEnvio fila;
 org.springframework.test.web.servlet.request.RequestPostProcessor leitor(UUID id,String...perms){TenantContext.set(tenant);var u=new br.com.servire.api.security.AuthenticatedUser(id,tenant,UsuarioTenant.Role.ADMIN);return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(u,null,Arrays.stream(perms).map(x->new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_"+x)).toList()));}
 tools.jackson.databind.JsonNode postJson(String rota,Object req)throws Exception{return json.readTree(mvc.perform(post(rota).header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(req))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 tools.jackson.databind.JsonNode putJson(String rota,Object req)throws Exception{return json.readTree(mvc.perform(put(rota).header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(req))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 Map<String,Object> aviso(String publico,List<UUID> ids,long versao){return Map.of("titulo","Aviso privado","descricao","Texto da versão","status","PUBLICADO","publico",publico,"destinatarios",ids,"versao",versao);}
 @Test void muralSelecionadoSoApareceAoPublicoAutorizado()throws Exception{
  var a=postJson("/mural/avisos",aviso("SELECIONADOS",List.of(usuario),0));String id=a.path("id").asString();
  mvc.perform(get("/mural/avisos/"+id).with(leitor(usuario,"MURAL"))).andExpect(status().isOk()).andExpect(jsonPath("$.destinatarios.length()").value(0));
  mvc.perform(get("/mural/avisos/"+id).with(leitor(outroUsuario,"MURAL"))).andExpect(status().isNotFound());
  mvc.perform(get("/mural/avisos").with(leitor(outroUsuario,"MURAL"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
 }
 @Test void muralNaoConcedePermissaoNemAceitaContaExterna()throws Exception{
  mvc.perform(post("/mural/avisos").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(aviso("SELECIONADOS",List.of(UUID.randomUUID()),0)))).andExpect(status().isBadRequest());
  var a=postJson("/mural/avisos",aviso("TODOS",List.of(),0));mvc.perform(get("/mural/avisos/"+a.path("id").asString()).with(leitor(usuario,"PESSOA"))).andExpect(status().isForbidden());
 }
 @Test void confirmacaoDeLeituraEhExplicitaIdempotenteEValeParaVersao()throws Exception{
  var a=postJson("/mural/avisos",aviso("TODOS",List.of(),0));String rota="/mural/avisos/"+a.path("id").asString();long v=a.path("versao").asLong();
  mvc.perform(get(rota).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.lido").value(false));
  putJson(rota+"/leitura",Map.of("versao",v));var r=putJson(rota+"/leitura",Map.of("versao",v));assertThat(r.path("leituras").asLong()).isEqualTo(1);
  r=putJson(rota,aviso("TODOS",List.of(),v));assertThat(r.path("lido").asBoolean()).isFalse();assertThat(r.path("leituras").asLong()).isZero();
  mvc.perform(put(rota+"/leitura").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(Map.of("versao",v)))).andExpect(status().isConflict());
 }
 @Test void leitorNaoConfirmaSemAcaoPropria()throws Exception{
  var a=postJson("/mural/avisos",aviso("TODOS",List.of(),0));mvc.perform(put("/mural/avisos/"+a.path("id").asString()+"/leitura").with(leitor(usuario,"MURAL")).contentType("application/json").content("{\"versao\":0}")).andExpect(status().isForbidden());
 }
 @Test void arquivarEscondeDeLeitorSemApagarTextoDaCoordenacao()throws Exception{
  var a=postJson("/mural/avisos",aviso("TODOS",List.of(),0));String rota="/mural/avisos/"+a.path("id").asString();var body=new HashMap<>(aviso("TODOS",List.of(),a.path("versao").asLong()));body.put("status","ARQUIVADO");putJson(rota,body);
  mvc.perform(get(rota).with(leitor(usuario,"MURAL"))).andExpect(status().isNotFound());mvc.perform(get(rota).header("Authorization","Bearer "+token)).andExpect(status().isOk());
 }
 @Test void consumoRegistraUmaUltimaConsultaPorDiaSemDadosPessoais()throws Exception{
  mvc.perform(get("/minha-conta/consumo").header("Authorization","Bearer "+token)).andExpect(status().isOk());mvc.perform(get("/minha-conta/consumo").header("Authorization","Bearer "+token)).andExpect(status().isOk());
  var r=mvc.perform(get("/minha-conta/consumo/historico").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andReturn().getResponse().getContentAsString();assertThat(r).doesNotContain("Pessoa A","Conta A","@teste.test");
 }
 @Test void historicoNaoCriaZerosParaDiasNaoConsultados()throws Exception{mvc.perform(get("/minha-conta/consumo/historico").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));}
 @Test void historiaMaisAntigaNaoSobrescreveConsultaMaisNova(){TenantContext.set(tenant);var atual=cotas.consumo();consumoHistorico.registrar(tenant,atual);var antiga=new CotasService.Consumo("Nome antigo",atual.versaoDireitos(),atual.direitosConfirmadosEm(),atual.consultadoEm().minusSeconds(1),atual.itens());consumoHistorico.registrar(tenant,antiga);assertThat(consumoHistorico.listar(tenant).getFirst().consumo().consultadoEm()).isEqualTo(atual.consultadoEm());}
 @Test void aniversarioNaoPresumeAutorizacaoENaoHabilitaAgendador()throws Exception{
  mvc.perform(get("/aniversarios/pessoas/"+pessoa).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].autorizado").value(false));mvc.perform(get("/aniversarios/configuracoes").header("Authorization","Bearer "+token)).andExpect(jsonPath("$[0].agendadorAtivo").value(false));
  TenantContext.set(tenant);assertThat(aniversarioService.candidatos(LocalDate.now(ZoneId.of("America/Sao_Paulo")))).isEmpty();
 }
 @Test void autorizacaoDeAniversarioTemVersaoERevogacaoPorCanal()throws Exception{
  String rota="/aniversarios/pessoas/"+pessoa+"/EMAIL";var r=putJson(rota,Map.of("autorizado",true,"fonte","Solicitação pessoal registrada","versao",0));assertThat(r.path("versao").asLong()).isEqualTo(1);
  mvc.perform(put(rota).header("Authorization","Bearer "+token).contentType("application/json").content("{\"autorizado\":false,\"fonte\":\"Revogado\",\"versao\":0}")).andExpect(status().isConflict());
  putJson(rota,Map.of("autorizado",false,"fonte","Pessoa solicitou revogação","versao",1));mvc.perform(get("/aniversarios/pessoas/"+pessoa).header("Authorization","Bearer "+token)).andExpect(jsonPath("$[0].autorizado").value(false)).andExpect(jsonPath("$[1].autorizado").value(false));
 }
 @Test void aniversarioRecusaPessoaDeOutraParoquiaELayoutExterno()throws Exception{
  mvc.perform(put("/aniversarios/pessoas/"+UUID.randomUUID()+"/EMAIL").header("Authorization","Bearer "+token).contentType("application/json").content("{\"autorizado\":true,\"fonte\":\"Pedido\",\"versao\":0}")).andExpect(status().isNotFound());
  mvc.perform(put("/aniversarios/configuracoes/EMAIL").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(Map.of("ativo",true,"layoutId",UUID.randomUUID(),"versao",0)))).andExpect(status().isBadRequest());
 }
 UUID prepararAniversario()throws Exception{
  TenantContext.set(tenant);var hoje=LocalDate.now(ZoneId.of("America/Sao_Paulo"));UUID layout=new TransactionTemplate(tm).execute(tx->{var p=em.find(Pessoa.class,pessoa);p.setDataNascimento(hoje.minusYears(20));var e=new PessoaEmail("PESSOAL","aniversario@example.test",true);e.setPessoa(p);p.getEmails().add(e);em.persist(e);var l=new Layout("Felicitação",TipoLayout.values()[0],TipoEnvio.EMAIL,"Felicitações","Parabéns!",true);em.persist(l);em.flush();return l.getId();});TenantContext.clear();
  putJson("/aniversarios/configuracoes/EMAIL",Map.of("ativo",true,"layoutId",layout,"versao",0));putJson("/aniversarios/pessoas/"+pessoa+"/EMAIL",Map.of("autorizado",true,"fonte","Autorização explícita","versao",0));TenantContext.set(tenant);return aniversarioService.candidatos(hoje).getFirst();
 }
 @Test void aniversarioAgendaUmaVezPorAnoSemDispararHttp()throws Exception{
  var id=prepararAniversario();var hoje=LocalDate.now(ZoneId.of("America/Sao_Paulo"));assertThat(aniversarioService.preparar(id,hoje)).isTrue();assertThat(aniversarioService.preparar(id,hoje)).isFalse();
  new TransactionTemplate(tm).executeWithoutResult(tx->{assertThat(em.createQuery("select count(x) from AniversarioExecucao x",Long.class).getSingleResult()).isEqualTo(1);assertThat(em.createQuery("select count(c) from Comunicado c",Long.class).getSingleResult()).isEqualTo(1);});
 }
 @Test void revogarAniversarioImpedeReservaDaFila()throws Exception{
  var id=prepararAniversario();var hoje=LocalDate.now(ZoneId.of("America/Sao_Paulo"));aniversarioService.preparar(id,hoje);TenantContext.clear();putJson("/aniversarios/pessoas/"+pessoa+"/EMAIL",Map.of("autorizado",false,"fonte","Pessoa revogou","versao",1));fila.processarAgora();TenantContext.set(tenant);new TransactionTemplate(tm).executeWithoutResult(tx->assertThat(em.createQuery("select d.status from ComunicadoDestinatario d",StatusEnvio.class).getSingleResult()).isEqualTo(StatusEnvio.FALHA));
 }
 @Test void aniversarioEm29FevereiroRespeitaAnoBissexto(){assertThat(AniversarioService.aniversario(LocalDate.of(2000,2,29),LocalDate.of(2027,2,28))).isTrue();assertThat(AniversarioService.aniversario(LocalDate.of(2000,2,29),LocalDate.of(2028,2,28))).isFalse();assertThat(AniversarioService.aniversario(LocalDate.of(2000,2,29),LocalDate.of(2028,2,29))).isTrue();}
 Map<String,Object> dadosSite(String titulo){return Map.of("titulo",titulo,"apresentacao","Apresentação pública <script>alert(1)</script>","endereco","Endereço autorizado","horarios","Domingo 19h","contato","Contato público","blocos",List.of(Map.of("tipo","AVISO","titulo","Aviso autorizado","texto","Texto público")));}
 String slug(){return tenants.findById(tenant).orElseThrow().getSlug();}
 @Test void paginaPublicaExigePublicacaoExplicitaEDraftNaoVaza()throws Exception{
  mvc.perform(get("/public/paroquias/"+slug())).andExpect(status().isNotFound());var e=putJson("/site-paroquia",Map.of("versao",0,"dados",dadosSite("Primeiro rascunho")));assertThat(e.path("versao").asLong()).isEqualTo(1);
  mvc.perform(get("/public/paroquias/"+slug())).andExpect(status().isNotFound());postJson("/site-paroquia/publicar",Map.of("versao",1,"confirmar",true));
  putJson("/site-paroquia",Map.of("versao",2,"dados",dadosSite("Novo rascunho privado")));
  var r=mvc.perform(get("/public/paroquias/"+slug())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.titulo").value("Primeiro rascunho")).andReturn().getResponse().getContentAsString();assertThat(r).doesNotContain("Novo rascunho privado","Pessoa A","tenantId","responsavelTelefone");
 }
 @Test void despublicarRetiraPaginaEPreservaRascunho()throws Exception{putJson("/site-paroquia",Map.of("versao",0,"dados",dadosSite("Título")));postJson("/site-paroquia/publicar",Map.of("versao",1,"confirmar",true));postJson("/site-paroquia/despublicar",Map.of("versao",2));mvc.perform(get("/public/paroquias/"+slug())).andExpect(status().isNotFound());mvc.perform(get("/site-paroquia").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.rascunho.titulo").value("Título"));}
 @Test void publicarRecusaVersaoAntigaEConfirmacaoAusente()throws Exception{putJson("/site-paroquia",Map.of("versao",0,"dados",dadosSite("Título")));mvc.perform(post("/site-paroquia/publicar").header("Authorization","Bearer "+token).contentType("application/json").content("{\"versao\":0,\"confirmar\":true}")).andExpect(status().isConflict());mvc.perform(post("/site-paroquia/publicar").header("Authorization","Bearer "+token).contentType("application/json").content("{\"versao\":1,\"confirmar\":false}")).andExpect(status().isBadRequest());}
 @Test void siteNaoVazaParaOutraParoquiaOuQuandoAcessoBloqueado()throws Exception{
  putJson("/site-paroquia",Map.of("versao",0,"dados",dadosSite("Título")));postJson("/site-paroquia/publicar",Map.of("versao",1,"confirmar",true));mvc.perform(get("/public/paroquias/"+UUID.randomUUID())).andExpect(status().isNotFound());var d=direitos.findById(tenant).orElseThrow();d.setAcessoLiberado(false);direitos.saveAndFlush(d);mvc.perform(get("/public/paroquias/"+slug())).andExpect(status().isNotFound());
 }
 @Test void permissaoConsultaSiteNaoPermitePublicar()throws Exception{mvc.perform(post("/site-paroquia/publicar").with(leitor(usuario,"SITE")).contentType("application/json").content("{\"versao\":0,\"confirmar\":true}")).andExpect(status().isForbidden());}
 @Test void outraParoquiaNaoRecebeMuralHistoricoConsentimentoOuRascunho()throws Exception{
  String avisoId=postJson("/mural/avisos",aviso("TODOS",List.of(),0)).path("id").asString();
  putJson("/site-paroquia",Map.of("versao",0,"dados",dadosSite("Rascunho privado A")));
  putJson("/aniversarios/pessoas/"+pessoa+"/EMAIL",Map.of("autorizado",true,"fonte","Pedido","versao",0));
  mvc.perform(get("/minha-conta/consumo").header("Authorization","Bearer "+token)).andExpect(status().isOk());
  String s=UUID.randomUUID().toString();var t=tenants.saveAndFlush(new Tenant(s,s,"Outra paróquia",Tenant.Status.ATIVO));var u=usuarios.saveAndFlush(new Usuario(s+"@teste.test","Outra conta"));vinculos.saveAndFlush(new UsuarioTenant(u,t,UsuarioTenant.Role.ADMIN,UsuarioTenant.Status.ATIVO));String outroToken=jwt.gerarAccessToken(u.getId(),t.getId(),UsuarioTenant.Role.ADMIN);
  mvc.perform(get("/mural/avisos/"+avisoId).header("Authorization","Bearer "+outroToken)).andExpect(status().isNotFound());
  mvc.perform(get("/mural/avisos").header("Authorization","Bearer "+outroToken)).andExpect(jsonPath("$.total").value(0));
  mvc.perform(get("/minha-conta/consumo/historico").header("Authorization","Bearer "+outroToken)).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(get("/aniversarios/pessoas/"+pessoa).header("Authorization","Bearer "+outroToken)).andExpect(status().isNotFound());
  mvc.perform(get("/site-paroquia").header("Authorization","Bearer "+outroToken)).andExpect(status().isOk()).andExpect(jsonPath("$.versao").value(0)).andExpect(jsonPath("$.rascunho.titulo").value("Outra paróquia"));
 }
 @Test void candidatosExcluemConfiguracaoDesligadaMesmoComConsentimento()throws Exception{
  prepararAniversario();TenantContext.clear();putJson("/aniversarios/configuracoes/EMAIL",Map.of("ativo",false,"versao",1));TenantContext.set(tenant);assertThat(aniversarioService.candidatos(LocalDate.now(ZoneId.of("America/Sao_Paulo")))).isEmpty();
 }
}
