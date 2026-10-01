package br.com.servire.api.minhaconta;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.EmailSender;
import br.com.servire.api.comunicacao.*;
import br.com.servire.api.comunicacao.dto.WhatsappConfigRequest;
import br.com.servire.api.integracao.*;
import br.com.servire.api.tenant.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class CotasEnviosIntegrationTest extends AbstractIntegrationTest {
    @Autowired TenantRepository tenants; @Autowired DireitosLocaisRepository direitos;
    @Autowired CotasService cotas; @Autowired EnvioAvulso avulso; @Autowired FilaDeEnvio fila;
    @Autowired ComunicadoService comunicados;
    @Autowired ParoquiaWhatsappService whatsapp; @Autowired ComunicadoDestinatarioRepository destinatarios;
    @Autowired org.springframework.transaction.PlatformTransactionManager tm;
    @Autowired MockMvc mvc;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
    @MockitoBean EmailSender email;
    @MockitoBean WhatsappSender zap;
    UUID tenant;
    @BeforeEach void preparar() { tenant=nova();TenantContext.set(tenant); }
    @AfterEach void limpar() {TenantContext.clear();}
    UUID nova() {String x=UUID.randomUUID().toString();return tenants.saveAndFlush(new Tenant(x,x,"Cotas envios",Tenant.Status.ATIVO)).getId();}
    void limite(String json) {
        var d=direitos.findById(tenant).orElseGet(() -> new DireitosLocais(tenant,UUID.randomUUID()));
        d.setLimites(json);d.setSituacao("ATIVA");d.setAcessoLiberado(true);d.setConfirmadoEm(Instant.now());direitos.saveAndFlush(d);
    }
    List<UUID> emails(int n) {
        var mensagens=new ArrayList<EnvioAvulso.MensagemEmail>();
        for(int i=0;i<n;i++) mensagens.add(new EnvioAvulso.MensagemEmail(null,"Pessoa",UUID.randomUUID()+"@example.test","Aviso","<p>Teste</p>"));
        return new org.springframework.transaction.support.TransactionTemplate(tm).execute(s -> avulso.enfileirarEmail("Evento","Aviso",mensagens));
    }
    CotasService.Item item(String codigo) {return cotas.consumo().itens().stream().filter(i -> i.codigo().equals(codigo)).findFirst().orElseThrow();}
    void rodadaAtual() {
        var paroquia=tenants.findById(tenant).orElseThrow();
        for(int i=0;i<4;i++) org.springframework.test.util.ReflectionTestUtils.invokeMethod(fila,"rodada",paroquia,List.of(TipoEnvio.values()));
    }
    void processar() {TenantContext.clear();rodadaAtual();TenantContext.set(tenant);}
    void tornarProntos(List<UUID> ids) {
        new org.springframework.transaction.support.TransactionTemplate(tm).executeWithoutResult(s -> em.createQuery("update ComunicadoDestinatario d set d.proximaTentativa=:quando where d.id in :ids")
          .setParameter("quando",Instant.EPOCH).setParameter("ids",ids).executeUpdate());
    }
    @Test void tetoMantemPendentesSemFalhaEAdicionalPermiteRetomar() throws Exception {
        limite("{\"emails_mes\":1}");var ids=emails(2);assertThat(item("emails_mes").usado()).isZero();processar();
        assertThat(item("emails_mes").usado()).isEqualTo(1);assertThat(item("emails_mes").estado()).isEqualTo("ATINGIDO");
        var linhas=destinatarios.findAllById(ids);
        assertThat(linhas).filteredOn(d -> d.getStatus()==StatusEnvio.PENDENTE).singleElement().satisfies(d -> {
            assertThat(d.getTentativas()).isZero();assertThat(d.getErro()).contains("cota mensal");assertThat(d.getCotaCompetencia()).isNull();});
        mvc.perform(get("/minha-conta/consumo").with(user("leitor").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_PAROQUIA"))))
          .andExpect(status().isOk()).andExpect(jsonPath("$.itens[4].competencia").value(item("emails_mes").competencia()));
        limite("{\"emails_mes\":2}");tornarProntos(ids);processar();assertThat(item("emails_mes").usado()).isEqualTo(2);
        assertThat(destinatarios.findAllById(ids)).allMatch(d -> d.getStatus()==StatusEnvio.ENVIADO);
    }
    @Test void falhaEReenvioDaMesmaMensagemNaoDuplicamConsumo() {
        limite("{\"emails_mes\":1}");var ids=emails(1);
        doThrow(new RuntimeException("provedor indisponível")).when(email).enviarComunicadoIdempotente(anyString(),anyString(),anyString(),anyList(),nullable(String.class),anyString());
        for(int i=0;i<3;i++) {tornarProntos(ids);processar();}
        assertThat(item("emails_mes").usado()).isEqualTo(1);
        assertThat(destinatarios.findById(ids.getFirst()).orElseThrow().getStatus()).isEqualTo(StatusEnvio.FALHA);
        comunicados.reenviarFalhas(destinatarios.findById(ids.getFirst()).orElseThrow().getComunicadoId());
        doNothing().when(email).enviarComunicadoIdempotente(anyString(),anyString(),anyString(),anyList(),nullable(String.class),anyString());
        processar();assertThat(item("emails_mes").usado()).isEqualTo(1);
        assertThat(destinatarios.findById(ids.getFirst()).orElseThrow().getStatus()).isEqualTo(StatusEnvio.ENVIADO);
    }
    @Test void canaisETenantsSaoIndependentesEZeroNaoChamaProvedor() {
        limite("{\"emails_mes\":0,\"whatsapp_mes\":1}");emails(1);
        whatsapp.salvar(new WhatsappConfigRequest("instancia","token-local-ficticio",true));
        new org.springframework.transaction.support.TransactionTemplate(tm).executeWithoutResult(s -> avulso.enfileirarWhatsapp("Evento",List.of(new EnvioAvulso.Mensagem(null,"Pessoa","5545999990000","Aviso"))));
        processar();assertThat(item("emails_mes").usado()).isZero();assertThat(item("whatsapp_mes").usado()).isEqualTo(1);
        verify(email,never()).enviarComunicadoIdempotente(anyString(),anyString(),anyString(),anyList(),nullable(String.class),anyString());
        verify(zap).enviarTexto(eq("instancia"),eq("token-local-ficticio"),eq("5545999990000"),eq("Aviso"));
        TenantContext.set(nova());assertThat(item("emails_mes").usado()).isZero();assertThat(item("whatsapp_mes").usado()).isZero();
    }
    @Test void doisWorkersRespeitamUmaUnidadeEHttpFicaForaDaTransacao() throws Exception {
        limite("{\"emails_mes\":1}");var ids=emails(2);TenantContext.clear();
        doAnswer(inv -> {assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return null;})
          .when(email).enviarComunicadoIdempotente(anyString(),anyString(),anyString(),anyList(),nullable(String.class),anyString());
        try(var e=Executors.newFixedThreadPool(2)) {var a=e.submit(this::rodadaAtual);var b=e.submit(this::rodadaAtual);a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);}
        TenantContext.set(tenant);assertThat(item("emails_mes").usado()).isEqualTo(1);
        assertThat(destinatarios.findAllById(ids)).filteredOn(d -> d.getStatus()==StatusEnvio.ENVIADO).hasSize(1);
    }
    @Test void mesAnteriorNaoOcupaMesAtualEConfiguracaoInvalidaNaoViraIlimitado() {
        limite("{\"emails_mes\":1}");var ids=emails(1);processar();
        new org.springframework.transaction.support.TransactionTemplate(tm).executeWithoutResult(s -> em.createQuery("update ComunicadoDestinatario d set d.cotaCompetencia=:mes where d.id=:id")
          .setParameter("mes",CotasService.competencia(Instant.now()).minusMonths(1)).setParameter("id",ids.getFirst()).executeUpdate());
        assertThat(item("emails_mes").usado()).isZero();emails(1);processar();assertThat(item("emails_mes").usado()).isEqualTo(1);
        limite("{\"emails_mes\":-1}");assertThatThrownBy(() -> cotas.consumo()).isInstanceOf(CotaException.class);
    }
    @Test void competenciaUsaViradaDoMesEmBrasilia() {
        assertThat(CotasService.competencia(Instant.parse("2026-11-01T02:59:59Z"))).isEqualTo(LocalDate.of(2026,10,1));
        assertThat(CotasService.competencia(Instant.parse("2026-11-01T03:00:00Z"))).isEqualTo(LocalDate.of(2026,11,1));
    }
}
