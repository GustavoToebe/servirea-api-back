package br.com.servire.api.organizacao;
import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.tenant.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@AutoConfigureMockMvc
class OrganizacaoHttpIntegrationTest extends AbstractIntegrationTest {
 @Autowired MockMvc mvc;@Autowired TenantRepository tenants;@Autowired JsonMapper json;
 UUID tenant;
 @BeforeEach void preparar(){String slug=UUID.randomUUID().toString();tenant=tenants.saveAndFlush(new Tenant(slug,slug,"Organização",Tenant.Status.ATIVO)).getId();TenantContext.set(tenant);}
 @AfterEach void limpar(){TenantContext.clear();}
 org.springframework.test.web.servlet.request.RequestPostProcessor admin(String perm){return user("admin").authorities(new SimpleGrantedAuthority("PERM_"+perm),new SimpleGrantedAuthority("PERM_"+perm+"_CRIAR"),new SimpleGrantedAuthority("PERM_"+perm+"_ALTERAR"));}
 String corpo(String titulo,String status,Long versao){return json.writeValueAsString(Map.of("titulo",titulo,"descricao","Descrição segura <script>texto</script>","status",status,"prazo","2026-10-15","versao",versao==null?0:versao,"equipe","Pastoral"));}
 String criar(String rota,String perm,String status)throws Exception {TenantContext.set(tenant);return mvc.perform(post(rota).with(csrf()).with(admin(perm)).contentType("application/json").content(corpo("Aviso 100%",status,null))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();}
 @ParameterizedTest @CsvSource({"/mural/avisos,MURAL,PUBLICADO,ARQUIVADO","/tarefas,TAREFA,ABERTA,CONCLUIDA"})
 void criarConsultarAtualizarERejeitarVersaoAntiga(String rota,String perm,String inicial,String finalizado)throws Exception {
  var n=json.readTree(criar(rota,perm,inicial));String id=n.path("id").asString();long versao=n.path("versao").asLong();
  TenantContext.set(tenant);mvc.perform(get(rota+"/"+id).with(admin(perm))).andExpect(status().isOk()).andExpect(jsonPath("$.titulo").value("Aviso 100%"));
  TenantContext.set(tenant);mvc.perform(put(rota+"/"+id).with(csrf()).with(admin(perm)).contentType("application/json").content(corpo("Atualizado",finalizado,versao))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(finalizado)).andExpect(jsonPath("$.versao").value(versao+1));
  TenantContext.set(tenant);mvc.perform(put(rota+"/"+id).with(csrf()).with(admin(perm)).contentType("application/json").content(corpo("Obsoleto",inicial,versao))).andExpect(status().isConflict());
 }
 @ParameterizedTest @CsvSource({"/mural/avisos,MURAL,PUBLICADO","/tarefas,TAREFA,ABERTA"})
 void outraParoquiaNaoLeNemEdita(String rota,String perm,String estado)throws Exception {
  String id=json.readTree(criar(rota,perm,estado)).path("id").asString();TenantContext.set(UUID.randomUUID());
  mvc.perform(get(rota+"/"+id).with(admin(perm))).andExpect(status().isNotFound());TenantContext.set(UUID.randomUUID());
  mvc.perform(put(rota+"/"+id).with(csrf()).with(admin(perm)).contentType("application/json").content(corpo("Invasão",estado,0L))).andExpect(status().isNotFound());
 }
 @ParameterizedTest @CsvSource({"/mural/avisos,MURAL,PUBLICADO","/tarefas,TAREFA,ABERTA"})
 void permissaoDeConsultaNaoAutorizaCriacao(String rota,String perm,String estado)throws Exception {
  mvc.perform(post(rota).with(csrf()).with(user("leitor").authorities(new SimpleGrantedAuthority("PERM_"+perm))).contentType("application/json").content(corpo("Novo",estado,null))).andExpect(status().isForbidden());
 }
 @ParameterizedTest @CsvSource({"/mural/avisos,MURAL,PUBLICADO","/tarefas,TAREFA,ABERTA"})
 void buscaLiteralEPaginacaoComTotal(String rota,String perm,String estado)throws Exception {
  criar(rota,perm,estado);TenantContext.set(tenant);mvc.perform(post(rota).with(csrf()).with(admin(perm)).contentType("application/json").content(corpo("Outra atividade",estado,null))).andExpect(status().isOk());
  TenantContext.set(tenant);mvc.perform(get(rota).param("busca","%").param("tamanho","1").with(admin(perm))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.itens[0].titulo").value("Aviso 100%"));
  TenantContext.set(tenant);mvc.perform(get(rota).param("tamanho","1").param("pagina","1").with(admin(perm))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.itens.length()").value(1));
  TenantContext.set(tenant);mvc.perform(get(rota).param("tamanho","101").with(admin(perm))).andExpect(status().isBadRequest());
 }
 @ParameterizedTest @CsvSource({"/mural/avisos,MURAL,PUBLICADO","/tarefas,TAREFA,ABERTA"})
 void validaTituloEDescricao(String rota,String perm,String estado)throws Exception {
  mvc.perform(post(rota).with(csrf()).with(admin(perm)).contentType("application/json").content(corpo(" ",estado,null))).andExpect(status().isBadRequest());
 }
}
