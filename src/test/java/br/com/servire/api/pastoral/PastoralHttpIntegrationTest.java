package br.com.servire.api.pastoral;
import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.tenant.*;
import br.com.servire.api.pessoa.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@AutoConfigureMockMvc class PastoralHttpIntegrationTest extends AbstractIntegrationTest {
 @Autowired MockMvc mvc;@Autowired TenantRepository tenants;@Autowired PessoaRepository pessoas;@Autowired JsonMapper json;UUID tenant,pessoa;
 @BeforeEach void preparar(){String slug=UUID.randomUUID().toString();tenant=tenants.saveAndFlush(new Tenant(slug,slug,"Equipes",Tenant.Status.ATIVO)).getId();TenantContext.set(tenant);pessoa=pessoas.saveAndFlush(new Pessoa(PessoaPapel.RESPONSAVEL,"Participante teste")).getId();}
 @AfterEach void limpar(){TenantContext.clear();}
 org.springframework.test.web.servlet.request.RequestPostProcessor admin(){return user("admin").authorities(new SimpleGrantedAuthority("PERM_PASTORAL"),new SimpleGrantedAuthority("PERM_PASTORAL_CRIAR"),new SimpleGrantedAuthority("PERM_PASTORAL_ALTERAR"),new SimpleGrantedAuthority("PERM_PASTORAL_GERENCIAR"));}
 String criar()throws Exception{TenantContext.set(tenant);return json.readTree(mvc.perform(post("/pastorais/equipes").with(csrf()).with(admin()).contentType("application/json").content("{\"nome\":\"Liturgia\",\"descricao\":null,\"ativo\":true,\"versao\":null}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asString();}
 String membro(UUID pessoa,String papel,Long versao){return "{\"pessoaId\":\""+pessoa+"\",\"papel\":\""+papel+"\",\"ativo\":true,\"versao\":"+versao+"}";}
 @Test void participanteCoordenadorEDuplicidadeComVersao()throws Exception{String id=criar();TenantContext.set(tenant);mvc.perform(post("/pastorais/equipes/"+id+"/membros").with(csrf()).with(admin()).contentType("application/json").content(membro(pessoa,"MEMBRO",null))).andExpect(status().isOk());TenantContext.set(tenant);mvc.perform(post("/pastorais/equipes/"+id+"/membros").with(csrf()).with(admin()).contentType("application/json").content(membro(pessoa,"COORDENADOR",null))).andExpect(status().isConflict());TenantContext.set(tenant);mvc.perform(post("/pastorais/equipes/"+id+"/membros").with(csrf()).with(admin()).contentType("application/json").content(membro(pessoa,"COORDENADOR",0L))).andExpect(status().isOk());TenantContext.set(tenant);mvc.perform(get("/pastorais/equipes/"+id+"/membros").with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].papel").value("COORDENADOR")).andExpect(jsonPath("$.itens[0].nome").value("Participante teste"));}
 @Test void outraParoquiaNaoAcessaEquipeNemPessoa()throws Exception{String id=criar();TenantContext.set(UUID.randomUUID());mvc.perform(get("/pastorais/equipes/"+id+"/membros").with(admin())).andExpect(status().isNotFound());TenantContext.set(tenant);mvc.perform(post("/pastorais/equipes/"+id+"/membros").with(csrf()).with(admin()).contentType("application/json").content(membro(UUID.randomUUID(),"MEMBRO",null))).andExpect(status().isNotFound());}
 @Test void leituraNaoAutorizaEscritaESemValidacaoNaoCriaEquipe()throws Exception{
  mvc.perform(post("/pastorais/equipes").with(csrf()).with(user("leitor").authorities(new SimpleGrantedAuthority("PERM_PASTORAL"))).contentType("application/json").content(json.writeValueAsString(Map.of("nome","Liturgia","ativo",true)))).andExpect(status().isForbidden());
  TenantContext.set(tenant);mvc.perform(post("/pastorais/equipes").with(csrf()).with(admin()).contentType("application/json").content(json.writeValueAsString(Map.of("nome"," ","ativo",true)))).andExpect(status().isBadRequest());
 }
 @Test void equipeInativaPreservaMembrosMasImpedeNovos()throws Exception{String id=criar();TenantContext.set(tenant);mvc.perform(put("/pastorais/equipes/"+id).with(csrf()).with(admin()).contentType("application/json").content("{\"nome\":\"Liturgia\",\"ativo\":false,\"versao\":0}")).andExpect(status().isOk());TenantContext.set(tenant);mvc.perform(post("/pastorais/equipes/"+id+"/membros").with(csrf()).with(admin()).contentType("application/json").content(membro(pessoa,"MEMBRO",null))).andExpect(status().isConflict());}
 @Test void buscaDePessoasSomenteOpcaoSemFichas()throws Exception{mvc.perform(get("/pastorais/pessoas").param("busca","Participante").with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$[0].nome").value("Participante teste")).andExpect(jsonPath("$[0].cpf").doesNotExist());}
}
