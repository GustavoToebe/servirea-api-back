package br.com.servire.api.pessoa;
import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@AutoConfigureMockMvc
class CuidadosHttpIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PessoaRepository pessoas;
    @Autowired TenantRepository tenants;
    @Autowired br.com.servire.api.auth.UsuarioRepository usuarios;
    private UUID tenant,id,usuarioId;
    @BeforeEach void preparar() {
        String x=UUID.randomUUID().toString();tenant=tenants.saveAndFlush(new Tenant(x,x,"Teste cuidados",Tenant.Status.ATIVO)).getId();TenantContext.set(tenant);
        usuarioId=usuarios.saveAndFlush(new br.com.servire.api.auth.Usuario(UUID.randomUUID()+"@teste.com","Teste")).getId();
        Pessoa p=new Pessoa(PessoaPapel.RESPONSAVEL,"Maria");p.setCondicoes(new CondicaoEspecial[]{CondicaoEspecial.TEA});p.setNivelSuporteTea(2);p.setCuidados("Informação restrita");
        id=pessoas.saveAndFlush(p).getId();
    }
    @AfterEach void limpar() { TenantContext.clear(); }
    private UsernamePasswordAuthenticationToken auth(String... permissoes) {
        TenantContext.set(tenant);
        return UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(usuarioId,tenant,UsuarioTenant.Role.ADMIN,false),null,
            Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toList());
    }
    @Test void leituraGeralNaoExibeDadosSensitivosNaListaOuDetalhe() throws Exception {
        mvc.perform(get("/pessoas/"+id).with(authentication(auth("PERM_PESSOA")))).andExpect(status().isOk())
            .andExpect(jsonPath("$.condicoes").isEmpty()).andExpect(jsonPath("$.cuidados").doesNotExist()).andExpect(jsonPath("$.nivelSuporteTea").doesNotExist());
        mvc.perform(get("/pessoas").with(authentication(auth("PERM_PESSOA")))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].condicoes").isEmpty()).andExpect(jsonPath("$[0].cuidados").doesNotExist());
    }
    @Test void permissaoEspecificaPermiteConsultar() throws Exception {
        mvc.perform(get("/pessoas/"+id).with(authentication(auth("PERM_PESSOA","PERM_PESSOA_CUIDADOS_LER"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cuidados").value("Informação restrita")).andExpect(jsonPath("$.condicoes[0]").value("TEA"));
    }
    @Test void editarCadastroSemCamposDeCuidadosPreservaInformacaoProtegida() throws Exception {
        mvc.perform(put("/pessoas/"+id).with(authentication(auth("PERM_PESSOA_ALTERAR"))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nomeCompleto\":\"Maria alterada\",\"papeis\":[\"RESPONSAVEL\"],\"condicoes\":[],\"cuidados\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cuidados").doesNotExist());
        TenantContext.set(tenant); var p=pessoas.findById(id).orElseThrow();
        assertThat(p.getCuidados()).isEqualTo("Informação restrita");assertThat(p.getCondicoes()).containsExactly(CondicaoEspecial.TEA);
    }
    @Test void editorGeralNaoPodeInjetarCuidadosNoPayload() throws Exception {
        mvc.perform(put("/pessoas/"+id).with(authentication(auth("PERM_PESSOA_ALTERAR"))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nomeCompleto\":\"Maria\",\"papeis\":[\"RESPONSAVEL\"],\"cuidados\":\"Novo cuidado\"}"))
            .andExpect(status().isForbidden());
        TenantContext.set(tenant);assertThat(pessoas.findById(id).orElseThrow().getCuidados()).isEqualTo("Informação restrita");
    }
}
