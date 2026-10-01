package br.com.servire.api.pessoa;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class PaginacaoPessoasHttpIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PessoaRepository pessoas;
    @Autowired TenantRepository tenants;
    @Autowired JsonMapper mapper;
    private UUID tenant;
    @BeforeEach void preparar() {
        String x=UUID.randomUUID().toString();tenant=tenants.saveAndFlush(new Tenant(x,x,"Paginação",Tenant.Status.ATIVO)).getId();TenantContext.set(tenant);
        for(int n=0;n<35;n++) {
            Pessoa p=new Pessoa(PessoaPapel.RESPONSAVEL,"Pessoa "+String.format("%02d",n));p.setCuidados("Reservado");
            pessoas.saveAndFlush(p);
        }
    }
    @AfterEach void limpar() {TenantContext.clear();}
    private UsernamePasswordAuthenticationToken auth(String... permissoes) {
        TenantContext.set(tenant);
        return UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(UUID.randomUUID(),tenant,UsuarioTenant.Role.ADMIN,false),null,
            Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toList());
    }
    @Test void paginasLimitadasNaoRepetemIdsENaoExibemCuidadosSemPermissao() throws Exception {
        var primeira=mvc.perform(get("/pessoas/pagina").with(authentication(auth("PERM_PESSOA"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(35)).andExpect(jsonPath("$.itens.length()").value(30))
            .andExpect(jsonPath("$.itens[0].cuidados").doesNotExist()).andReturn();
        var segunda=mvc.perform(get("/pessoas/pagina").param("pagina","1").with(authentication(auth("PERM_PESSOA"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens.length()").value(5)).andExpect(jsonPath("$.itens[0].nomeCompleto").value("Pessoa 30")).andReturn();
        Set<String> ids=new HashSet<>();
        for(var resultado:List.of(primeira,segunda)) for(var item:mapper.readTree(resultado.getResponse().getContentAsString()).path("itens")) assertThat(ids.add(item.path("id").asText())).isTrue();
        assertThat(ids).hasSize(35);
    }
    @Test void filtroAplicaAntesDaPaginacaoELimitesSaoValidados() throws Exception {
        mvc.perform(get("/pessoas/pagina").param("nome","Pessoa 34").with(authentication(auth("PERM_PESSOA"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.itens[0].nomeCompleto").value("Pessoa 34"));
        for(var parametro:List.of(new String[]{"pagina","-1"},new String[]{"tamanho","101"},new String[]{"tamanho","0"}))
            mvc.perform(get("/pessoas/pagina").param(parametro[0],parametro[1]).with(authentication(auth("PERM_PESSOA")))).andExpect(status().isBadRequest());
    }
    @Test void resumoNaoContaOutraParoquiaEOpcaoNaoIncluiFichaCompleta() throws Exception {
        String x=UUID.randomUUID().toString();UUID outra=tenants.saveAndFlush(new Tenant(x,x,"Outra",Tenant.Status.ATIVO)).getId();
        TenantContext.set(outra);pessoas.saveAndFlush(new Pessoa(PessoaPapel.RESPONSAVEL,"Outra pessoa"));
        mvc.perform(get("/pessoas/resumo").with(authentication(auth("PERM_PESSOA"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.pessoas").value(35)).andExpect(jsonPath("$.ativos").value(0));
        mvc.perform(get("/pessoas/opcoes").param("limite","2").with(authentication(auth("PERM_PESSOA"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].cuidados").doesNotExist())
            .andExpect(jsonPath("$[0].emails").doesNotExist());
    }
    @Test void endpointsNovosExigemPermissaoDoModulo() throws Exception {
        for(String path:List.of("/pessoas/pagina","/pessoas/resumo","/pessoas/opcoes","/inscricoes/pagina"))
            mvc.perform(get(path).with(authentication(auth("PERM_ESCALA")))).andExpect(status().isForbidden());
    }
}
