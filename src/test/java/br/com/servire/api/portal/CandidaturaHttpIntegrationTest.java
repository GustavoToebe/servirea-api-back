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
class CandidaturaHttpIntegrationTest extends AbstractIntegrationTest {
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
  });plano("PORTAL_VOLUNTARIO","CALENDARIO","MURAL","PASTORAIS","ESCALAS");token=jwt.gerarAccessToken(usuario,tenant,UsuarioTenant.Role.ADMIN);TenantContext.clear();
 }
 Pessoa pessoa(String nome){var p=pessoas.saveAndFlush(new Pessoa(PessoaPapel.VOLUNTARIO,nome));var v=new Voluntario();v.setPessoa(p);v.setTipo(TipoVoluntario.COROINHA);v.setFuncoesHabilitadas(new FuncaoEscala[]{FuncaoEscala.MISSAL});voluntarios.saveAndFlush(v);return p;}
 void escala(Pessoa p,StatusEscala status,boolean referencia,String titulo){var s=new Escala(titulo,TipoEscala.MENSAL);s.setStatus(status);var e=new EscalaEvento(dia,LocalTime.of(19,0),titulo);e.setEscala(s);e.setReferencia(referencia);s.getEventos().add(e);var vaga=new EscalaVaga(FuncaoEscala.values()[0],1);vaga.setEvento(e);vaga.setVoluntario(voluntarios.findById(p.getId()).orElseThrow());e.getVagas().add(vaga);escalas.saveAndFlush(s);}
 void evento(Pessoa p,String titulo,Evento.Situacao status){var e=new Evento(titulo,dia.atTime(20,0));e.setSituacao(status);eventos.saveAndFlush(e);inscritos.saveAndFlush(new EventoInscricao(e.getId(),p,false));}
 void plano(String...codigos){var d=direitos.findById(tenant).orElseGet(()->new DireitosLocais(tenant,UUID.randomUUID()));d.setSituacao("ATIVA");d.setAcessoLiberado(true);d.setConfirmadoEm(Instant.now());d.setFuncionalidades(codigos);direitos.saveAndFlush(d);}
 String assinatura()throws Exception {return json.readTree(mvc.perform(post("/calendario/assinatura").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asString();}
 @AfterEach void limpar(){TenantContext.clear();}


 @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
 UUID novaVaga(){return novaVaga(LocalTime.of(21,0));}
 UUID novaVaga(LocalTime hora){TenantContext.set(tenant);try{return new TransactionTemplate(tm).execute(tx->{var s=new Escala("Vagas",TipoEscala.MENSAL);s.setStatus(StatusEscala.FINALIZADA);var e=new EscalaEvento(dia,hora,"Celebração aberta");e.setEscala(s);s.getEventos().add(e);var v=new EscalaVaga(FuncaoEscala.MISSAL,1);v.setEvento(e);e.getVagas().add(v);escalas.saveAndFlush(s);return v.getId();});}finally{TenantContext.clear();}}
 UUID escalaId(UUID vaga){TenantContext.set(tenant);try{return new TransactionTemplate(tm).execute(tx->em.find(EscalaVaga.class,vaga).getEvento().getEscala().getId());}finally{TenantContext.clear();}}
 tools.jackson.databind.JsonNode pedir(UUID vaga,String bearer)throws Exception{return json.readTree(mvc.perform(post("/portal/vagas/"+vaga+"/candidaturas").header("Authorization","Bearer "+bearer).contentType("application/json").content("{\"versao\":0}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 int decidir(UUID id,boolean aprovar,long versao)throws Exception{return mvc.perform(put("/escalas/candidaturas/"+id+"/decisao").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(Map.of("aprovar",aprovar,"versao",versao)))).andReturn().getResponse().getStatus();}
 UUID id(tools.jackson.databind.JsonNode p){return UUID.fromString(p.path("id").asString());}
 @Test void vagaLivreNaoExpoePessoasEPedidoNaoReservaVaga()throws Exception{
  UUID vaga=novaVaga();mvc.perform(get("/portal/vagas-abertas").param("de",dia.toString()).param("ate",dia.toString()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.itens[0].elegivel").value(true)).andExpect(jsonPath("$.itens[0].pessoaNome").doesNotExist());
  var p=pedir(vaga,token);assertThat(p.path("situacao").asString()).isEqualTo("PENDENTE");assertThat(id(pedir(vaga,token))).isEqualTo(id(p));
  TenantContext.set(tenant);try{new TransactionTemplate(tm).executeWithoutResult(tx->assertThat(em.find(EscalaVaga.class,vaga).getVoluntario()).isNull());}finally{TenantContext.clear();}
 }
 @Test void aprovaAlocaEmFinalizadaEPessoaPodeResponderDepois()throws Exception{
  UUID vaga=novaVaga();var p=pedir(vaga,token);assertThat(decidir(id(p),true,p.path("versao").asLong())).isEqualTo(200);
  mvc.perform(get("/portal/candidaturas").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.itens[0].situacao").value("APROVADA"));
  TenantContext.set(tenant);try{new TransactionTemplate(tm).executeWithoutResult(tx->{var v=em.find(EscalaVaga.class,vaga);assertThat(v.getVoluntario().getId()).isEqualTo(pessoa);assertThat(v.getResposta()).isEqualTo(RespostaParticipacao.PENDENTE);assertThat(v.getEvento().getEscala().getStatus()).isEqualTo(StatusEscala.FINALIZADA);});}finally{TenantContext.clear();}
  assertThat(decidir(id(p),true,p.path("versao").asLong())).isEqualTo(409);
 }
 @Test void recusaNaoAlocaENaoPermiteReabrirPedidoRecusadoNoMesmoCiclo()throws Exception{
  UUID vaga=novaVaga();var p=pedir(vaga,token);assertThat(decidir(id(p),false,0)).isEqualTo(200);
  mvc.perform(post("/portal/vagas/"+vaga+"/candidaturas").header("Authorization","Bearer "+token).contentType("application/json").content("{\"versao\":0}")).andExpect(status().isConflict());
 }
 @Test void podeDesistirERenovarSemDuplicarCandidatura()throws Exception{
  UUID vaga=novaVaga();var p=pedir(vaga,token);mvc.perform(put("/portal/candidaturas/"+id(p)+"/desistencia").header("Authorization","Bearer "+token).contentType("application/json").content("{\"versao\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("DESISTIDA"));
  var novo=pedir(vaga,token);assertThat(id(novo)).isEqualTo(id(p));assertThat(novo.path("versao").asLong()).isGreaterThan(0);
  mvc.perform(get("/portal/candidaturas").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.total").value(1));
 }
 @Test void duasAprovacoesDaMesmaVagaSoAlocamUmaPessoa()throws Exception{
  UUID vaga=novaVaga();String outroToken=jwt.gerarAccessToken(outroUsuario,tenant,UsuarioTenant.Role.ADMIN);var a=pedir(vaga,token);var b=pedir(vaga,outroToken);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var fa=pool.submit(()->decidir(id(a),true,0));var fb=pool.submit(()->decidir(id(b),true,0));assertThat(List.of(fa.get(20,java.util.concurrent.TimeUnit.SECONDS),fb.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}
  mvc.perform(get("/escalas/"+escalaId(vaga)+"/candidaturas").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.total").value(2));
 }
 @Test void segundaVagaNoMesmoHorarioBloqueiaAprovacao()throws Exception{
  var a=pedir(novaVaga(),token);var b=pedir(novaVaga(),token);assertThat(decidir(id(a),true,0)).isEqualTo(200);assertThat(decidir(id(b),true,0)).isEqualTo(409);
 }
 @Test void elegibilidadeRevalidaAtivoFuncaoDisponibilidadeEIndisponibilidade()throws Exception{
  UUID vaga=novaVaga();TenantContext.set(tenant);try{new TransactionTemplate(tm).executeWithoutResult(tx->{var v=voluntarios.findById(pessoa).orElseThrow();v.setAtivo(false);});}finally{TenantContext.clear();}
  mvc.perform(post("/portal/vagas/"+vaga+"/candidaturas").header("Authorization","Bearer "+token).contentType("application/json").content("{\"versao\":0}")).andExpect(status().isConflict());
  TenantContext.set(tenant);try{new TransactionTemplate(tm).executeWithoutResult(tx->{var v=voluntarios.findById(pessoa).orElseThrow();v.setAtivo(true);v.setFuncoesHabilitadas(new FuncaoEscala[]{FuncaoEscala.CRUZ});});}finally{TenantContext.clear();}
  mvc.perform(post("/portal/vagas/"+vaga+"/candidaturas").header("Authorization","Bearer "+token).contentType("application/json").content("{\"versao\":0}")).andExpect(status().isConflict());
  TenantContext.set(tenant);try{new TransactionTemplate(tm).executeWithoutResult(tx->{var v=voluntarios.findById(pessoa).orElseThrow();v.setFuncoesHabilitadas(new FuncaoEscala[]{FuncaoEscala.MISSAL});em.persist(new DisponibilidadeVoluntario(v,dia.getDayOfWeek(),null,Periodo.MANHA,null));});}finally{TenantContext.clear();}
  mvc.perform(post("/portal/vagas/"+vaga+"/candidaturas").header("Authorization","Bearer "+token).contentType("application/json").content("{\"versao\":0}")).andExpect(status().isConflict());
  TenantContext.set(tenant);try{new TransactionTemplate(tm).executeWithoutResult(tx->{em.createQuery("delete from DisponibilidadeVoluntario d where d.voluntario.id=:pessoa").setParameter("pessoa",pessoa).executeUpdate();em.persist(new Indisponibilidade(pessoa,dia,null,null));});}finally{TenantContext.clear();}
  mvc.perform(post("/portal/vagas/"+vaga+"/candidaturas").header("Authorization","Bearer "+token).contentType("application/json").content("{\"versao\":0}")).andExpect(status().isConflict());
 }
 @Test void mudancaDeVinculoDepoisDoPedidoImpedeAprovar()throws Exception{
  var p=pedir(novaVaga(),token);var v=vinculos.findByUsuario_IdAndTenant_Id(usuario,tenant).orElseThrow();v.setPessoaId(null);vinculos.saveAndFlush(v);assertThat(decidir(id(p),true,0)).isEqualTo(409);
 }
 @Test void reaberturaInvalidaCandidaturaEHistoricoPersisteAoExcluir()throws Exception{
  UUID vaga=novaVaga();var p=pedir(vaga,token);UUID escala=escalaId(vaga);TenantContext.set(tenant);try{escalaService.reabrir(escala);escalaService.finalizar(escala);}finally{TenantContext.clear();}
  assertThat(decidir(id(p),true,0)).isEqualTo(409);mvc.perform(get("/portal/candidaturas").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.itens[0].situacao").value("EXPIRADA"));
  TenantContext.set(tenant);long ciclo;try{ciclo=new TransactionTemplate(tm).execute(tx->em.find(EscalaVaga.class,vaga).getRespostaVersao());}finally{TenantContext.clear();}
  assertThat(ciclo).isGreaterThan(0);
  mvc.perform(post("/portal/vagas/"+vaga+"/candidaturas").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(Map.of("versao",ciclo)))).andExpect(status().isOk());
  TenantContext.set(tenant);try{escalaService.cancelar(escala);escalaService.excluir(escala);}finally{TenantContext.clear();}
  mvc.perform(get("/portal/candidaturas").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.total").value(2));
 }
 @Test void paginaTrintaVagasEDtoVersaoObrigatoria()throws Exception{
  for(int i=0;i<32;i++)novaVaga();mvc.perform(get("/portal/vagas-abertas").param("de",dia.toString()).param("ate",dia.toString()).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.total").value(32)).andExpect(jsonPath("$.itens.length()").value(30));
  mvc.perform(get("/portal/vagas-abertas").param("de",dia.toString()).param("ate",dia.toString()).param("pagina","1").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.itens.length()").value(2));
  mvc.perform(post("/portal/vagas/"+novaVaga()+"/candidaturas").header("Authorization","Bearer "+token).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
 }
 @Test void planoEPermissaoProtegemEscritaEDesistenciaPodeAposDowngrade()throws Exception{
  UUID vaga=novaVaga();var p=pedir(vaga,token);plano();mvc.perform(post("/portal/vagas/"+vaga+"/candidaturas").header("Authorization","Bearer "+token).contentType("application/json").content("{\"versao\":0}")).andExpect(status().isForbidden());assertThat(decidir(id(p),true,0)).isEqualTo(403);
  mvc.perform(put("/portal/candidaturas/"+id(p)+"/desistencia").header("Authorization","Bearer "+token).contentType("application/json").content("{\"versao\":0}")).andExpect(status().isOk());
  var v=vinculos.findByUsuario_IdAndTenant_Id(usuario,tenant).orElseThrow();v.setRole(UsuarioTenant.Role.VISUALIZADOR);vinculos.saveAndFlush(v);assertThat(decidir(id(p),true,0)).isEqualTo(403);
 }
 @Test void minhaListaNaoIncluiOutroCandidatoENaoPossoDesistirPorEle()throws Exception{
  UUID vaga=novaVaga();var outro=pedir(vaga,jwt.gerarAccessToken(outroUsuario,tenant,UsuarioTenant.Role.ADMIN));mvc.perform(get("/portal/candidaturas").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.total").value(0));
  mvc.perform(put("/portal/candidaturas/"+id(outro)+"/desistencia").header("Authorization","Bearer "+token).contentType("application/json").content("{\"versao\":0}")).andExpect(status().isNotFound());
 }
 @Test void vagaDeOutraParoquiaNaoPodeSerVistaOuSolicitada()throws Exception{
  UUID original=tenant;var t=tenants.saveAndFlush(new Tenant(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"Externa",Tenant.Status.ATIVO));
  tenant=t.getId();UUID vaga=novaVaga();UUID escala=escalaId(vaga);tenant=original;
  mvc.perform(post("/portal/vagas/"+vaga+"/candidaturas").header("Authorization","Bearer "+token).contentType("application/json").content("{\"versao\":0}")).andExpect(status().isNotFound());
  mvc.perform(get("/escalas/"+escala+"/candidaturas").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
  mvc.perform(get("/portal/vagas-abertas").param("de",dia.toString()).param("ate",dia.toString()).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.total").value(0));
 }
 @Test void aprovacaoRevalidaFuncaoEPrazoExpiradoNaoAceitaPedido()throws Exception{
  UUID vaga=novaVaga();var p=pedir(vaga,token);TenantContext.set(tenant);try{new TransactionTemplate(tm).executeWithoutResult(tx->voluntarios.findById(pessoa).orElseThrow().setFuncoesHabilitadas(new FuncaoEscala[]{}));}finally{TenantContext.clear();}
  assertThat(decidir(id(p),true,0)).isEqualTo(409);
  TenantContext.set(tenant);try{new TransactionTemplate(tm).executeWithoutResult(tx->em.find(EscalaVaga.class,vaga).getEvento().setData(dia.minusDays(20)));}finally{TenantContext.clear();}
  mvc.perform(post("/portal/vagas/"+vaga+"/candidaturas").header("Authorization","Bearer "+token).contentType("application/json").content("{\"versao\":0}")).andExpect(status().isConflict());
 }
}
