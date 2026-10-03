package br.com.servire.api.integracao;
import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.tenant.*;
import br.com.servire.api.comunicacao.*;
import br.com.servire.api.auth.EmailSender;
import br.com.servire.api.inscricao.TurnstileService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@AutoConfigureMockMvc
class FuncionalidadesPlanoHttpIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc; @Autowired TenantRepository tenants; @Autowired DireitosLocaisRepository direitos;
    @Autowired EnvioAvulso avulso; @Autowired FilaDeEnvio fila; @Autowired ComunicadoDestinatarioRepository destinatarios;
    @Autowired br.com.servire.api.evento.EventoService eventos;
    @Autowired org.springframework.transaction.PlatformTransactionManager tm;
    @MockitoBean EmailSender email; @MockitoBean TurnstileService turnstile;
    UUID tenant;String slug;
    @BeforeEach void preparar() {
        doCallRealMethod().when(funcionalidadesPlano).liberadas();
        slug=UUID.randomUUID().toString();tenant=tenants.saveAndFlush(new Tenant(slug,slug,"Plano",Tenant.Status.ATIVO)).getId();TenantContext.set(tenant);
    }
    @AfterEach void limpar() {TenantContext.clear();}
    void plano(String... codigos) {
        var d=direitos.findById(tenant).orElseGet(() -> new DireitosLocais(tenant,UUID.randomUUID()));
        d.setSituacao("ATIVA");d.setAcessoLiberado(true);d.setConfirmadoEm(Instant.now());d.setFuncionalidades(codigos);direitos.saveAndFlush(d);
    }
    @Test void semSnapshotOuListaVaziaNaoLiberaEOuTroTenantNaoHerda() throws Exception {
        assertThat(funcionalidadesPlano.liberadas()).isEmpty();plano();assertThat(funcionalidadesPlano.liberadas()).isEmpty();
        plano("FINANCEIRO","codigo_desconhecido","eventos");
        mvc.perform(get("/funcionalidades-plano").with(user("usuario"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0]").value("FINANCEIRO"));
        TenantContext.set(UUID.randomUUID());assertThat(funcionalidadesPlano.liberadas()).isEmpty();
    }
    @Test void downgradePreservaLeituraMasBloqueiaNovasOperacoes() throws Exception {
        plano("FINANCEIRO");
        mvc.perform(post("/financeiro/contas").with(csrf()).with(user("admin").authorities(new SimpleGrantedAuthority("PERM_FINANCEIRO_CONFIGURAR")))
            .contentType("application/json").content("{\"nome\":\"Caixa\",\"saldoInicial\":0,\"dataSaldoInicial\":\"2026-10-01\",\"ativo\":true}"))
            .andExpect(status().isOk());
        TenantContext.set(tenant);plano();
        mvc.perform(get("/financeiro/contas").with(user("leitor").authorities(new SimpleGrantedAuthority("PERM_FINANCEIRO"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].nome").value("Caixa"));
        TenantContext.set(tenant);
        mvc.perform(post("/financeiro/contas").with(csrf()).with(user("admin").authorities(new SimpleGrantedAuthority("PERM_FINANCEIRO_CONFIGURAR")))
            .contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("FUNCIONALIDADE_NAO_CONTRATADA"));
    }
    @Test void planoNaoSubstituiPermissaoDoPerfil() throws Exception {
        plano("FINANCEIRO");
        mvc.perform(post("/financeiro/categorias").with(csrf()).with(user("leitor").authorities(new SimpleGrantedAuthority("PERM_FINANCEIRO")))
            .contentType("application/json").content("{\"nome\":\"Infra\",\"ativo\":true,\"tipo\":\"DESPESA\"}"))
            .andExpect(status().isForbidden());
    }
    @Test void todosOsModulosBloqueiamMutacoesAntesDoDominio() throws Exception {
        plano();
        for(String rota:List.of("/mural/avisos","/tarefas","/escalas","/eventos","/layouts","/inscricoes/"+UUID.randomUUID()+"/aprovar")) {
            TenantContext.set(tenant);
            mvc.perform(post(rota).with(csrf()).with(user("admin")).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("FUNCIONALIDADE_NAO_CONTRATADA"));
        }
        TenantContext.set(tenant);
        mvc.perform(multipart("/comunicados").with(csrf()).with(user("admin"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("FUNCIONALIDADE_NAO_CONTRATADA"));
        TenantContext.set(tenant);
        mvc.perform(multipart("/pessoas/importacoes/previa").file(new MockMultipartFile("arquivo","p.csv","text/csv","nome;papel;cpf;email;telefone".getBytes())).with(csrf()).with(user("admin")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("FUNCIONALIDADE_NAO_CONTRATADA"));
    }
    @Test void formularioPublicoNaoBypassaListaDoPlano() throws Exception {
        plano();
        var dados=new MockMultipartFile("dados","","application/json","{\"turnstileToken\":\"teste\",\"nomeCompleto\":\"Candidato\",\"tipo\":\"COROINHA\",\"autorizaWhatsapp\":false,\"consentimentoCuidados\":false}".getBytes());
        mvc.perform(multipart("/public/"+slug+"/inscricoes").file(dados)).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("FUNCIONALIDADE_NAO_CONTRATADA"));
    }
    @Test void filaPausaNoDowngradeESemConsumirTentativaRetomaNoUpgrade() {
        plano("COMUNICACAO");
        var ids=new org.springframework.transaction.support.TransactionTemplate(tm).execute(s -> avulso.enfileirarEmail("Evento","Aviso",List.of(new EnvioAvulso.MensagemEmail(null,"Pessoa","p@example.test","Aviso","<p>Teste</p>"))));
        plano();TenantContext.clear();fila.processarAgora();TenantContext.set(tenant);
        verify(email,never()).enviarComunicadoIdempotente(anyString(),anyString(),anyString(),anyList(),nullable(String.class),anyString());
        assertThat(destinatarios.findById(ids.getFirst()).orElseThrow().getTentativas()).isZero();
        assertThat(destinatarios.findById(ids.getFirst()).orElseThrow().getCotaCompetencia()).isNull();
        assertThat(eventos.enviarLembretes()).isZero();
        plano("COMUNICACAO");TenantContext.clear();fila.processarAgora();TenantContext.set(tenant);
        assertThat(destinatarios.findById(ids.getFirst()).orElseThrow().getStatus()).isEqualTo(StatusEnvio.ENVIADO);
    }
}
