package br.com.servire.api.onboarding;
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
class OnboardingHttpIntegrationTest extends AbstractIntegrationTest {
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
  });plano("PORTAL_VOLUNTARIO","CALENDARIO","MURAL","PASTORAIS","ESCALAS","TAREFAS");token=jwt.gerarAccessToken(usuario,tenant,UsuarioTenant.Role.ADMIN);TenantContext.clear();
 }
 Pessoa pessoa(String nome){var p=pessoas.saveAndFlush(new Pessoa(PessoaPapel.VOLUNTARIO,nome));var v=new Voluntario();v.setPessoa(p);v.setTipo(TipoVoluntario.COROINHA);v.setFuncoesHabilitadas(new FuncaoEscala[]{FuncaoEscala.MISSAL});voluntarios.saveAndFlush(v);return p;}
 void escala(Pessoa p,StatusEscala status,boolean referencia,String titulo){var s=new Escala(titulo,TipoEscala.MENSAL);s.setStatus(status);var e=new EscalaEvento(dia,LocalTime.of(19,0),titulo);e.setEscala(s);e.setReferencia(referencia);s.getEventos().add(e);var vaga=new EscalaVaga(FuncaoEscala.values()[0],1);vaga.setEvento(e);vaga.setVoluntario(voluntarios.findById(p.getId()).orElseThrow());e.getVagas().add(vaga);escalas.saveAndFlush(s);}
 void evento(Pessoa p,String titulo,Evento.Situacao status){var e=new Evento(titulo,dia.atTime(20,0));e.setSituacao(status);eventos.saveAndFlush(e);inscritos.saveAndFlush(new EventoInscricao(e.getId(),p,false));}
 void plano(String...codigos){var d=direitos.findById(tenant).orElseGet(()->new DireitosLocais(tenant,UUID.randomUUID()));d.setSituacao("ATIVA");d.setAcessoLiberado(true);d.setConfirmadoEm(Instant.now());d.setFuncionalidades(codigos);direitos.saveAndFlush(d);}
 String assinatura()throws Exception {return json.readTree(mvc.perform(post("/calendario/assinatura").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asString();}
 @AfterEach void limpar(){TenantContext.clear();}



 @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;

 String body(String acao,long versao){return json.writeValueAsString(Map.of("acao",acao,"versao",versao));}
 tools.jackson.databind.JsonNode alterar(String etapa,String acao,long versao)throws Exception {return json.readTree(mvc.perform(put("/onboarding/etapas/"+etapa).header("Authorization","Bearer "+token).contentType("application/json").content(body(acao,versao))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 tools.jackson.databind.JsonNode consultar()throws Exception{return json.readTree(mvc.perform(get("/onboarding").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 @Test void consultaMostraPreRequisitosSemConcluirNemCriarProgresso()throws Exception {
  var r=consultar();assertThat(r.path("versao").asLong()).isZero();assertThat(r.path("concluidas").asInt()).isZero();assertThat(r.path("total").asInt()).isEqualTo(5);assertThat(r.path("etapas").get(0).path("situacao").asString()).isEqualTo("PRONTA");
  TenantContext.set(tenant);try{new TransactionTemplate(tm).executeWithoutResult(tx->assertThat(em.createQuery("select count(p) from OnboardingProgresso p",Long.class).getSingleResult()).isZero());}finally{TenantContext.clear();}
 }
 @Test void revisaEtapasERetomaEntreSessoesDaEquipe()throws Exception {
  String[] etapas={"PAROQUIA","CONVITE","PESSOAS","VOLUNTARIOS","ESCALA"};for(int i=0;i<etapas.length;i++){var r=alterar(etapas[i],"CONCLUIR",i);assertThat(r.path("versao").asLong()).isEqualTo(i+1);assertThat(r.path("concluidas").asInt()).isEqualTo(i+1);}
  var r=consultar();assertThat(r.path("percentual").asInt()).isEqualTo(100);assertThat(r.path("proximaEtapa").isNull()).isTrue();assertThat(r.path("iniciadoEm").isNull()).isFalse();
  mvc.perform(get("/onboarding").header("Authorization","Bearer "+jwt.gerarAccessToken(outroUsuario,tenant,UsuarioTenant.Role.ADMIN))).andExpect(jsonPath("$.versao").value(5)).andExpect(jsonPath("$.concluidas").value(5));
  alterar("PESSOAS","REABRIR",5);assertThat(consultar().path("concluidas").asInt()).isEqualTo(4);
 }
 @Test void versionamentoImpedeTelaAntigaSobrescrever()throws Exception {
  alterar("PAROQUIA","CONCLUIR",0);mvc.perform(put("/onboarding/etapas/PAROQUIA").header("Authorization","Bearer "+token).contentType("application/json").content(body("REABRIR",0))).andExpect(status().isConflict());assertThat(consultar().path("concluidas").asInt()).isEqualTo(1);
 }
 @Test void primeiraGravacaoConcorrenteSoAceitaUmaRevisao()throws Exception {
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var a=pool.submit(()->mvc.perform(put("/onboarding/etapas/PAROQUIA").header("Authorization","Bearer "+token).contentType("application/json").content(body("CONCLUIR",0))).andReturn().getResponse().getStatus());var b=pool.submit(()->mvc.perform(put("/onboarding/etapas/PESSOAS").header("Authorization","Bearer "+token).contentType("application/json").content(body("CONCLUIR",0))).andReturn().getResponse().getStatus());assertThat(List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}
  assertThat(consultar().path("versao").asInt()).isEqualTo(1);
 }
 @Test void convitePodeSerDispensadoEReabertoSemEnviarConvites()throws Exception {
  var r=alterar("CONVITE","PULAR",0);assertThat(r.path("etapas").get(1).path("situacao").asString()).isEqualTo("DISPENSADA");assertThat(r.path("concluidas").asInt()).isEqualTo(1);alterar("CONVITE","REABRIR",1);assertThat(consultar().path("concluidas").asInt()).isZero();
  mvc.perform(put("/onboarding/etapas/ESCALA").header("Authorization","Bearer "+token).contentType("application/json").content(body("PULAR",2))).andExpect(status().isBadRequest());
 }
 @Test void dadosRemovidosPedemNovaRevisaoSemApagarHistorico()throws Exception {
  alterar("VOLUNTARIOS","CONCLUIR",0);TenantContext.set(tenant);try{new TransactionTemplate(tm).executeWithoutResult(tx->em.createQuery("update Voluntario v set v.ativo=false").executeUpdate());}finally{TenantContext.clear();}
  var r=consultar();assertThat(r.path("etapas").get(3).path("situacao").asString()).isEqualTo("REVISAR");assertThat(r.path("concluidas").asInt()).isZero();
  mvc.perform(put("/onboarding/etapas/VOLUNTARIOS").header("Authorization","Bearer "+token).contentType("application/json").content(body("CONCLUIR",1))).andExpect(status().isBadRequest());
 }
 @Test void semRecursoEscalasNaoVerificaNemPermiteConcluir()throws Exception {
  plano("PORTAL_VOLUNTARIO");var r=consultar();assertThat(r.path("total").asInt()).isEqualTo(4);assertThat(r.path("etapas").get(4).path("situacao").asString()).isEqualTo("NAO_CONTRATADA");assertThat(r.path("etapas").get(4).path("podeConcluir").asBoolean()).isFalse();mvc.perform(put("/onboarding/etapas/ESCALA").header("Authorization","Bearer "+token).contentType("application/json").content(body("CONCLUIR",0))).andExpect(status().isForbidden());
 }
 @Test void outraParoquiaTemProgressoIndependente()throws Exception {
  alterar("PAROQUIA","CONCLUIR",0);var t=tenants.saveAndFlush(new Tenant(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"Outra",Tenant.Status.ATIVO));vinculos.saveAndFlush(new UsuarioTenant(usuarios.findById(usuario).orElseThrow(),t,UsuarioTenant.Role.ADMIN,UsuarioTenant.Status.ATIVO));String outro=jwt.gerarAccessToken(usuario,t.getId(),UsuarioTenant.Role.ADMIN);
  mvc.perform(get("/onboarding").header("Authorization","Bearer "+outro)).andExpect(status().isOk()).andExpect(jsonPath("$.versao").value(0)).andExpect(jsonPath("$.concluidas").value(0)).andExpect(jsonPath("$.etapas[2].situacao").value("PENDENTE"));assertThat(consultar().path("concluidas").asInt()).isEqualTo(1);
 }
 @Test void checklistNaoConcedeAcessoAOutrosModulos()throws Exception {
  TenantContext.set(tenant);mvc.perform(get("/onboarding").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("leitor").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_ONBOARDING")))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.etapas[0].situacao").value("SEM_PERMISSAO")).andExpect(jsonPath("$.etapas[0].url").isEmpty());
  TenantContext.set(tenant);mvc.perform(put("/onboarding/etapas/PESSOAS").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("editor").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_ONBOARDING"),new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_ONBOARDING_GERENCIAR"))).contentType("application/json").content(body("CONCLUIR",0))).andExpect(status().isForbidden());
 }
 @Test void consultaNaoAutorizaRegistrarRevisao()throws Exception {
  TenantContext.set(tenant);mvc.perform(put("/onboarding/etapas/PAROQUIA").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("leitor").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_ONBOARDING"),new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_PAROQUIA"))).contentType("application/json").content(body("CONCLUIR",0))).andExpect(status().isForbidden());
 }
 @Test void dtoInvalidoNaoCriaProgresso()throws Exception {
  mvc.perform(put("/onboarding/etapas/PESSOAS").header("Authorization","Bearer "+token).contentType("application/json").content("{}")).andExpect(status().isBadRequest());mvc.perform(put("/onboarding/etapas/PESSOAS").header("Authorization","Bearer "+token).contentType("application/json").content(body("CONCLUIR",-1))).andExpect(status().isBadRequest());assertThat(consultar().path("versao").asInt()).isZero();
 }
}
