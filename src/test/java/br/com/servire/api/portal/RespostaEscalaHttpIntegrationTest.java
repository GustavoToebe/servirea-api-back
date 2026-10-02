package br.com.servire.api.portal;
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
class RespostaEscalaHttpIntegrationTest extends AbstractIntegrationTest {
 @Autowired MockMvc mvc;@Autowired TenantRepository tenants;@Autowired UsuarioRepository usuarios;@Autowired UsuarioTenantRepository vinculos;
 @Autowired PessoaRepository pessoas;@Autowired VoluntarioRepository voluntarios;@Autowired EscalaRepository escalas;@Autowired EventoRepository eventos;@Autowired EventoInscricaoRepository inscritos;
 @Autowired DireitosLocaisRepository direitos;@Autowired JwtService jwt;@Autowired JsonMapper json;@Autowired CalendarioRepository calendarios;@Autowired PlatformTransactionManager tm;
 @Autowired EscalaService escalaService;
 UUID tenant,usuario,pessoa,outroUsuario;String token;LocalDate dia;
 @BeforeEach void preparar(){doCallRealMethod().when(funcionalidadesPlano).liberadas();String slug=UUID.randomUUID().toString();var paroquia=tenants.saveAndFlush(new Tenant(slug,slug,"Portal",Tenant.Status.ATIVO));tenant=paroquia.getId();TenantContext.set(tenant);dia=LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(10);
  var u=usuarios.saveAndFlush(new Usuario(slug+"@teste.test","Conta A"));usuario=u.getId();var v=usuarios.saveAndFlush(new Usuario("b-"+slug+"@teste.test","Conta B"));outroUsuario=v.getId();
  new TransactionTemplate(tm).executeWithoutResult(tx->{var a=pessoa("Pessoa A");var b=pessoa("Pessoa B");pessoa=a.getId();var va=new UsuarioTenant(u,paroquia,UsuarioTenant.Role.ADMIN,UsuarioTenant.Status.ATIVO);va.setPessoaId(a.getId());vinculos.saveAndFlush(va);var vb=new UsuarioTenant(v,paroquia,UsuarioTenant.Role.ADMIN,UsuarioTenant.Status.ATIVO);vb.setPessoaId(b.getId());vinculos.saveAndFlush(vb);
   escala(a,StatusEscala.FINALIZADA,false,"Missa A");escala(b,StatusEscala.FINALIZADA,false,"Missa B");escala(a,StatusEscala.RASCUNHO,false,"Rascunho privado");escala(a,StatusEscala.FINALIZADA,true,"Linha de referência");
   evento(a,"Evento A",Evento.Situacao.PUBLICADO);evento(b,"Evento B",Evento.Situacao.PUBLICADO);evento(a,"Rascunho evento",Evento.Situacao.RASCUNHO);
  });plano("PORTAL_VOLUNTARIO","CALENDARIO","MURAL","PASTORAIS");token=jwt.gerarAccessToken(usuario,tenant,UsuarioTenant.Role.ADMIN);TenantContext.clear();
 }
 Pessoa pessoa(String nome){var p=pessoas.saveAndFlush(new Pessoa(PessoaPapel.VOLUNTARIO,nome));var v=new Voluntario();v.setPessoa(p);v.setTipo(TipoVoluntario.COROINHA);voluntarios.saveAndFlush(v);return p;}
 void escala(Pessoa p,StatusEscala status,boolean referencia,String titulo){var s=new Escala(titulo,TipoEscala.MENSAL);s.setStatus(status);var e=new EscalaEvento(dia,LocalTime.of(19,0),titulo);e.setEscala(s);e.setReferencia(referencia);s.getEventos().add(e);var vaga=new EscalaVaga(FuncaoEscala.values()[0],1);vaga.setEvento(e);vaga.setVoluntario(voluntarios.findById(p.getId()).orElseThrow());e.getVagas().add(vaga);escalas.saveAndFlush(s);}
 void evento(Pessoa p,String titulo,Evento.Situacao status){var e=new Evento(titulo,dia.atTime(20,0));e.setSituacao(status);eventos.saveAndFlush(e);inscritos.saveAndFlush(new EventoInscricao(e.getId(),p,false));}
 void plano(String...codigos){var d=direitos.findById(tenant).orElseGet(()->new DireitosLocais(tenant,UUID.randomUUID()));d.setSituacao("ATIVA");d.setAcessoLiberado(true);d.setConfirmadoEm(Instant.now());d.setFuncionalidades(codigos);direitos.saveAndFlush(d);}
 String assinatura()throws Exception {return json.readTree(mvc.perform(post("/calendario/assinatura").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asString();}
 @AfterEach void limpar(){TenantContext.clear();}

 UUID vaga() throws Exception {
  return UUID.fromString(json.readTree(mvc.perform(get("/portal/compromissos").param("de",dia.toString()).param("ate",dia.toString()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("compromissos").get(0).path("vagaId").asString());
 }

 long versao(UUID vaga) throws Exception {
  var itens=json.readTree(mvc.perform(get("/portal/compromissos").param("de",dia.toString()).param("ate",dia.toString()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("compromissos");
  for(var item:itens)if(item.path("vagaId").asString().equals(vaga.toString()))return item.path("versao").asLong();
  throw new AssertionError("Vaga não encontrada");
 }
 int responder(UUID vaga,String resposta,long versao)throws Exception {
  return mvc.perform(put("/portal/vagas/"+vaga+"/resposta").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(Map.of("resposta",resposta,"versao",versao)))).andReturn().getResponse().getStatus();
 }
 UUID escalaId(UUID vaga){TenantContext.set(tenant);try{return new TransactionTemplate(tm).execute(tx->entityManager().createQuery("select v.evento.escala.id from EscalaVaga v where v.id=:id",UUID.class).setParameter("id",vaga).getSingleResult());}finally{TenantContext.clear();}}
 @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
 jakarta.persistence.EntityManager entityManager(){return em;}

 @Test void confirmaRecusaSemDesalocarEHistoricoNaoDuplica()throws Exception {
  UUID id=vaga();assertThat(responder(id,"CONFIRMADA",versao(id))).isEqualTo(200);
  long atual=versao(id);assertThat(responder(id,"CONFIRMADA",atual)).isEqualTo(200);assertThat(versao(id)).isEqualTo(atual);
  assertThat(responder(id,"RECUSADA",atual)).isEqualTo(200);
  mvc.perform(get("/portal/vagas/"+id+"/respostas").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.itens[0].resposta").value("RECUSADA"));
  TenantContext.set(tenant);try{new TransactionTemplate(tm).executeWithoutResult(tx->{var v=em.find(EscalaVaga.class,id);assertThat(v.getVoluntario().getId()).isEqualTo(pessoa);assertThat(v.getPresenca()).isEqualTo(Presenca.PENDENTE);});}finally{TenantContext.clear();}
 }
 @Test void respostaObsoletaNaoSobrescreveNova()throws Exception {
  UUID id=vaga();long antes=versao(id);assertThat(responder(id,"CONFIRMADA",antes)).isEqualTo(200);assertThat(responder(id,"RECUSADA",antes)).isEqualTo(409);
 }
 @Test void naoRespondePorOutraPessoaOuParoquia()throws Exception {
  UUID outra;TenantContext.set(tenant);try{outra=new TransactionTemplate(tm).execute(tx->em.createQuery("select v.id from EscalaVaga v where v.voluntario.id<>:pessoa and v.evento.escala.status=:status",UUID.class).setParameter("pessoa",pessoa).setParameter("status",StatusEscala.FINALIZADA).getResultList().getFirst());}finally{TenantContext.clear();}
  assertThat(responder(outra,"CONFIRMADA",0)).isEqualTo(404);
  var t=tenants.saveAndFlush(new Tenant(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"Outra",Tenant.Status.ATIVO));
  TenantContext.set(t.getId());UUID estrangeira;try{estrangeira=new TransactionTemplate(tm).execute(tx->{var p=pessoa("Pessoa externa");escala(p,StatusEscala.FINALIZADA,false,"Externa");return em.createQuery("select v.id from EscalaVaga v",UUID.class).getSingleResult();});}finally{TenantContext.clear();}
  assertThat(responder(estrangeira,"CONFIRMADA",0)).isEqualTo(404);
  mvc.perform(get("/escalas/"+t.getId()+"/respostas").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
 }
 @Test void prazoEncerradoBloqueiaSemGravarHistorico()throws Exception {
  UUID id=vaga();long v=versao(id);TenantContext.set(tenant);try{new TransactionTemplate(tm).executeWithoutResult(tx->{var e=em.find(EscalaVaga.class,id).getEvento();e.setData(LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(1));});}finally{TenantContext.clear();}
  assertThat(responder(id,"CONFIRMADA",v)).isEqualTo(409);
  mvc.perform(get("/portal/vagas/"+id+"/respostas").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.total").value(0));
 }
 @Test void reabrirInvalidaRespostaETrocarPessoaMantemHistoricoAnterior()throws Exception {
  UUID id=vaga();assertThat(responder(id,"CONFIRMADA",versao(id))).isEqualTo(200);UUID escalaId=escalaId(id);long anterior=versao(id);
  TenantContext.set(tenant);try{escalaService.reabrir(escalaId);escalaService.finalizar(escalaId);}finally{TenantContext.clear();}
  assertThat(responder(id,"RECUSADA",anterior)).isEqualTo(409);
  TenantContext.set(tenant);try{escalaService.reabrir(escalaId);var b=vinculos.findByUsuario_IdAndTenant_Id(outroUsuario,tenant).orElseThrow().getPessoaId();escalaService.alocarVaga(id,b);escalaService.finalizar(escalaId);}finally{TenantContext.clear();}
  assertThat(responder(id,"CONFIRMADA",0)).isEqualTo(404);
  mvc.perform(get("/portal/vagas/"+id+"/respostas").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
 }
 @Test void cancelamentoBloqueiaEHistoricoSobreviveExclusao()throws Exception {
  UUID id=vaga();assertThat(responder(id,"CONFIRMADA",versao(id))).isEqualTo(200);UUID escalaId=escalaId(id);
  TenantContext.set(tenant);try{escalaService.cancelar(escalaId);}finally{TenantContext.clear();}
  assertThat(responder(id,"RECUSADA",0)).isEqualTo(404);
  TenantContext.set(tenant);try{escalaService.excluir(escalaId);}finally{TenantContext.clear();}
  mvc.perform(get("/portal/vagas/"+id+"/respostas").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
 }
 @Test void planoPermissaoEDtoSaoObrigatorios()throws Exception {
  UUID id=vaga();assertThat(responder(id,"PENDENTE",versao(id))).isEqualTo(400);
  mvc.perform(put("/portal/vagas/"+id+"/resposta").header("Authorization","Bearer "+token).contentType("application/json").content("{\"resposta\":\"CONFIRMADA\"}")).andExpect(status().isBadRequest());
  plano();assertThat(responder(id,"CONFIRMADA",0)).isEqualTo(403);plano("PORTAL_VOLUNTARIO");
  var v=vinculos.findByUsuario_IdAndTenant_Id(usuario,tenant).orElseThrow();v.setRole(UsuarioTenant.Role.VISUALIZADOR);vinculos.saveAndFlush(v);
  assertThat(responder(id,"CONFIRMADA",0)).isEqualTo(403);
  mvc.perform(get("/escalas/"+escalaId(id)+"/respostas").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
 }
 @Test void coordenacaoFiltraComVersoesEValoresIndependentesDePresenca()throws Exception {
  UUID id=vaga();assertThat(responder(id,"RECUSADA",versao(id))).isEqualTo(200);
  mvc.perform(get("/escalas/"+escalaId(id)+"/respostas").param("resposta","RECUSADA").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.itens[0].pessoaNome").value("Pessoa A"));
  mvc.perform(get("/portal/vagas/"+id+"/respostas").param("pagina","-1").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
 }
 @Test void duasRespostasConcorrentesNaoPerdemAtualizacao()throws Exception {
  UUID id=vaga();long v=versao(id);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var a=pool.submit(()->responder(id,"CONFIRMADA",v));var b=pool.submit(()->responder(id,"RECUSADA",v));
   assertThat(List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
  }
  mvc.perform(get("/portal/vagas/"+id+"/respostas").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.total").value(1));
 }
}
