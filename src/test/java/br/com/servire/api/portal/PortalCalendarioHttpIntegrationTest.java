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
class PortalCalendarioHttpIntegrationTest extends AbstractIntegrationTest {
 @Autowired MockMvc mvc;@Autowired TenantRepository tenants;@Autowired UsuarioRepository usuarios;@Autowired UsuarioTenantRepository vinculos;
 @Autowired PessoaRepository pessoas;@Autowired VoluntarioRepository voluntarios;@Autowired EscalaRepository escalas;@Autowired EventoRepository eventos;@Autowired EventoInscricaoRepository inscritos;
 @Autowired DireitosLocaisRepository direitos;@Autowired JwtService jwt;@Autowired JsonMapper json;@Autowired CalendarioRepository calendarios;@Autowired PlatformTransactionManager tm;
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
 @Test void jwtPessoalMostraSomenteCompromissosPublicadosDaPropriaPessoa()throws Exception{
  mvc.perform(get("/portal/compromissos").param("de",dia.toString()).param("ate",dia.toString()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.vinculado").value(true)).andExpect(jsonPath("$.compromissos.length()").value(2)).andExpect(jsonPath("$.compromissos[0].titulo").value("Missa A")).andExpect(jsonPath("$.compromissos[1].titulo").value("Evento A"));
 }
 @Test void semVinculoNaoInferimosPessoaPorEmailOuNome()throws Exception{var v=vinculos.findByUsuario_IdAndTenant_Id(usuario,tenant).orElseThrow();v.setPessoaId(null);vinculos.saveAndFlush(v);mvc.perform(get("/portal/compromissos").param("de",dia.toString()).param("ate",dia.toString()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.vinculado").value(false)).andExpect(jsonPath("$.compromissos").isEmpty());}
 @Test void calendarioPrivadoNaoIncluiOutrasPessoasETokenFicaSomenteEmHash()throws Exception{String segredo=assinatura();assertThat(calendarios.findByTenantIdAndUsuarioId(tenant,usuario).orElseThrow().tokenHash).doesNotContain(segredo);var r=mvc.perform(get("/public/calendario/"+segredo+".ics")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();assertThat(r).contains("SUMMARY:Missa A","SUMMARY:Evento A","DTSTART:","CLASS:PRIVATE").doesNotContain("Missa B","Evento B","Rascunho");}
 @Test void rotacaoRevogacaoEVinculoInativoInvalidamLink()throws Exception{String velho=assinatura();String novo=assinatura();mvc.perform(get("/public/calendario/"+velho+".ics")).andExpect(status().isNotFound());mvc.perform(get("/public/calendario/"+novo+".ics")).andExpect(status().isOk());var v=vinculos.findByUsuario_IdAndTenant_Id(usuario,tenant).orElseThrow();v.setStatus(UsuarioTenant.Status.INATIVO);vinculos.saveAndFlush(v);mvc.perform(get("/public/calendario/"+novo+".ics")).andExpect(status().isNotFound());v.setStatus(UsuarioTenant.Status.ATIVO);vinculos.saveAndFlush(v);mvc.perform(delete("/calendario/assinatura").header("Authorization","Bearer "+token)).andExpect(status().isOk());mvc.perform(get("/public/calendario/"+novo+".ics")).andExpect(status().isNotFound());}
 @Test void downgradeBloqueiaPortalEFeedMasPermiteRevogar()throws Exception{String segredo=assinatura();plano();mvc.perform(get("/portal/compromissos").param("de",dia.toString()).param("ate",dia.toString()).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());mvc.perform(get("/public/calendario/"+segredo+".ics")).andExpect(status().isNotFound());mvc.perform(delete("/calendario/assinatura").header("Authorization","Bearer "+token)).andExpect(status().isOk());}
 @Test void administradorNaoAssociaPessoaJaVinculadaOuDeOutraParoquia()throws Exception{mvc.perform(put("/usuarios/"+outroUsuario+"/pessoa").header("Authorization","Bearer "+token).contentType("application/json").content("{\"pessoaId\":\""+pessoa+"\"}")).andExpect(status().isConflict());mvc.perform(put("/usuarios/"+usuario+"/pessoa").header("Authorization","Bearer "+token).contentType("application/json").content("{\"pessoaId\":\""+UUID.randomUUID()+"\"}")).andExpect(status().isNotFound());}
 @Test void removerVinculoRevogaAssinatura()throws Exception{String segredo=assinatura();mvc.perform(put("/usuarios/"+usuario+"/pessoa").header("Authorization","Bearer "+token).contentType("application/json").content("{\"pessoaId\":null}")).andExpect(status().isOk());mvc.perform(get("/public/calendario/"+segredo+".ics")).andExpect(status().isNotFound());}
 @Test void expiracaoETokenInvalidoNaoLiberamCalendario()throws Exception {
  String segredo=assinatura();var c=calendarios.findByTenantIdAndUsuarioId(tenant,usuario).orElseThrow();c.expiraEm=Instant.now().minusSeconds(1);calendarios.saveAndFlush(c);
  mvc.perform(get("/public/calendario/"+segredo+".ics")).andExpect(status().isNotFound());
  mvc.perform(get("/public/calendario/invalido.ics")).andExpect(status().isNotFound());
 }
 @Test void perdaDePermissaoInvalidaFeedMesmoComPlanoContratado()throws Exception {
  String segredo=assinatura();var v=vinculos.findByUsuario_IdAndTenant_Id(usuario,tenant).orElseThrow();v.setRole(UsuarioTenant.Role.VISUALIZADOR);vinculos.saveAndFlush(v);
  mvc.perform(get("/public/calendario/"+segredo+".ics")).andExpect(status().isNotFound());
  mvc.perform(get("/portal/compromissos").param("de",dia.toString()).param("ate",dia.toString()).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
 }
 @Test void homologacaoDePlanoRealMantemHistoricoEBloqueiaMutacaoNaoContratada()throws Exception{mvc.perform(post("/financeiro/contas").header("Authorization","Bearer "+token).contentType("application/json").content("{}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("FUNCIONALIDADE_NAO_CONTRATADA"));mvc.perform(get("/financeiro/contas").header("Authorization","Bearer "+token)).andExpect(status().isOk());mvc.perform(post("/mural/avisos").header("Authorization","Bearer "+token).contentType("application/json").content("{\"titulo\":\"Homologação\",\"descricao\":\"Aviso\",\"status\":\"PUBLICADO\",\"prazo\":null,\"versao\":null}")).andExpect(status().isOk());}
}
