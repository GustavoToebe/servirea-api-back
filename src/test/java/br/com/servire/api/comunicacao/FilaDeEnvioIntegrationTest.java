package br.com.servire.api.comunicacao;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.EmailSender;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.CriarRequest;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.Criado;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.Detalhe;
import br.com.servire.api.comunicacao.dto.LayoutRequest;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaPapel;
import br.com.servire.api.pessoa.PessoaService;
import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.PessoaRequest;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FilaDeEnvioIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean private EmailSender emailSender;
    @MockitoBean private WhatsappSender whatsappSender;

    @Autowired private FilaDeEnvio fila;
    @Autowired private ComunicadoService service;
    @Autowired private LayoutService layoutService;
    @Autowired private PessoaService pessoaService;
    @Autowired private TenantRepository tenantRepository;

    @org.junit.jupiter.api.BeforeEach
    void prepararIdempotencia() {
        org.mockito.Mockito.doCallRealMethod().when(emailSender).enviarComunicadoIdempotente(anyString(),anyString(),anyString(),anyList(),org.mockito.ArgumentMatchers.nullable(String.class),anyString());
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    /** Paróquia com N pessoas, cada uma com um e-mail, e um comunicado para todas. */
    private Criado comunicado(Tenant.Status status, String... emails) {
        String s = UUID.randomUUID().toString();
        UUID tenantId = tenantRepository.saveAndFlush(new Tenant("FE-" + s.substring(0, 8), "fe-" + s, "Paróquia Fila", Tenant.Status.ATIVO)).getId();
        TenantContext.set(tenantId);
        List<UUID> ids = new ArrayList<>();
        for (String email : emails) {
            Pessoa p = pessoaService.criar(new PessoaRequest(Set.of(PessoaPapel.RESPONSAVEL), "Pessoa " + email, null, null, null, null,
                    List.of(new ContatoEmailRequest("Pessoal", email, true)), List.of(), List.of(), List.of(),
                    null, null, null, null, null, null, null, null, null, null, null, null, null));
            ids.add(p.getId());
        }
        Layout l = layoutService.criar(new LayoutRequest("Aviso " + s, TipoLayout.TODOS, TipoEnvio.EMAIL, "Aviso", "<p>Olá #PESSOA.PRIMEIRO_NOME#</p>", true));
        Criado criado = service.criar(new CriarRequest(TipoEnvio.EMAIL, l.getId(), null, EnviarPara.PESSOA, QuaisContatos.PRINCIPAL, ids), List.of());
        if (status != Tenant.Status.ATIVO) {
            TenantContext.clear();
            Tenant t = tenantRepository.findById(tenantId).orElseThrow();
            t.setStatus(status);
            tenantRepository.saveAndFlush(t);
            TenantContext.set(tenantId);
        }
        return criado;
    }

    private Detalhe detalhe(Criado c) {
        return service.detalhe(c.id());
    }

    @Test
    void enviaTodosEConclui() {
        String s = UUID.randomUUID().toString();
        Criado c = comunicado(Tenant.Status.ATIVO, "a-" + s + "@t.com", "b-" + s + "@t.com", "c-" + s + "@t.com");
        UUID tenantId = TenantContext.get();
        TenantContext.clear();

        fila.processarAgora();

        TenantContext.set(tenantId);
        Detalhe d = detalhe(c);
        assertThat(d.comunicado().status()).isEqualTo(StatusComunicado.CONCLUIDO);
        assertThat(d.comunicado().enviados()).isEqualTo(3);
        assertThat(d.destinatarios()).allMatch(x -> x.status() == StatusEnvio.ENVIADO && x.enviadoEm() != null);
        verify(emailSender).enviarComunicado(eq("a-" + s + "@t.com"), eq("Aviso"), eq("<p>Olá Pessoa</p>"), anyList(), any());
    }

    @Test
    void tresFalhasViramFalhaEReenviarVoltaParaAFila() {
        String email = "falha-" + UUID.randomUUID() + "@t.com";
        doThrow(new RuntimeException("fora do ar")).when(emailSender)
                .enviarComunicado(eq(email), anyString(), anyString(), anyList(), any());
        Criado c = comunicado(Tenant.Status.ATIVO, email);
        UUID tenantId = TenantContext.get();
        TenantContext.clear();

        fila.processarAgora();
        fila.processarAgora();
        fila.processarAgora();

        TenantContext.set(tenantId);
        Detalhe d = detalhe(c);
        assertThat(d.comunicado().status()).isEqualTo(StatusComunicado.CONCLUIDO);
        assertThat(d.comunicado().falhas()).isEqualTo(1);
        assertThat(d.destinatarios().getFirst().status()).isEqualTo(StatusEnvio.FALHA);
        assertThat(d.destinatarios().getFirst().erro()).contains("Falha no envio");

        service.reenviarFalhas(c.id());
        d = detalhe(c);
        assertThat(d.comunicado().status()).isEqualTo(StatusComunicado.NA_FILA);
        assertThat(d.destinatarios().getFirst().status()).isEqualTo(StatusEnvio.PENDENTE);
    }

    @Test
    void paroquiaBloqueadaNaoEProcessada() {
        String email = "bloq-" + UUID.randomUUID() + "@t.com";
        Criado c = comunicado(Tenant.Status.BLOQUEADO, email);
        UUID tenantId = TenantContext.get();
        TenantContext.clear();

        fila.processarAgora();

        verify(emailSender, never()).enviarComunicado(eq(email), anyString(), anyString(), anyList(), any());
        TenantContext.set(tenantId);
        assertThat(detalhe(c).comunicado().status()).isEqualTo(StatusComunicado.NA_FILA);
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test
    void doisWorkersNaoEnviamAMesmaMensagemEHttpFicaForaDaTransacao() throws Exception {
        String email="conc-"+UUID.randomUUID()+"@t.com"; Criado c=comunicado(Tenant.Status.ATIVO,email);
        UUID tenantId=TenantContext.get(); TenantContext.clear();
        var iniciou=new java.util.concurrent.CountDownLatch(1); var liberar=new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(inv -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            iniciou.countDown(); assertThat(liberar.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); return null;
        }).when(emailSender).enviarComunicado(eq(email),anyString(),anyString(),anyList(),any());
        try (var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var primeiro=executor.submit(fila::processarAgora);
            assertThat(iniciou.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try { executor.submit(fila::processarAgora).get(5,java.util.concurrent.TimeUnit.SECONDS); }
            finally { liberar.countDown(); }
            primeiro.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        verify(emailSender,org.mockito.Mockito.times(1)).enviarComunicado(eq(email),anyString(),anyString(),anyList(),any());
        TenantContext.set(tenantId); assertThat(detalhe(c).comunicado().enviados()).isEqualTo(1);
    }
    @Test
    void reservaExpiradaRetomaMasReservaAtivaNaoReenvia() {
        String email="crash-"+UUID.randomUUID()+"@t.com"; Criado c=comunicado(Tenant.Status.ATIVO,email);
        UUID tenantId=TenantContext.get();
        jdbc.update("update comunicado_destinatario set reservado_por=?,reserva_ate=? where comunicado_id=?",UUID.randomUUID(),java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(120)),c.id());
        TenantContext.clear();fila.processarAgora(); verify(emailSender,never()).enviarComunicado(eq(email),anyString(),anyString(),anyList(),any());
        jdbc.update("update comunicado_destinatario set reserva_ate=? where comunicado_id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),c.id());
        fila.processarAgora();
        verify(emailSender,org.mockito.Mockito.times(1)).enviarComunicado(eq(email),anyString(),anyString(),anyList(),any());
        TenantContext.set(tenantId);assertThat(detalhe(c).comunicado().status()).isEqualTo(StatusComunicado.CONCLUIDO);
    }
    @Test
    void conclusaoAntigaNaoAlteraNovaPosse() {
        String email="posse-"+UUID.randomUUID()+"@t.com"; Criado c=comunicado(Tenant.Status.ATIVO,email);
        UUID tenantId=TenantContext.get(); UUID dono=UUID.randomUUID();TenantContext.clear();
        org.mockito.Mockito.doAnswer(inv -> {
            jdbc.update("update comunicado_destinatario set reservado_por=? where comunicado_id=?",dono,c.id());return null;
        }).when(emailSender).enviarComunicado(eq(email),anyString(),anyString(),anyList(),any());
        fila.processarAgora();
        assertThat(jdbc.queryForObject("select reservado_por from comunicado_destinatario where comunicado_id=?",UUID.class,c.id())).isEqualTo(dono);
        TenantContext.set(tenantId);assertThat(detalhe(c).comunicado().enviados()).isZero();
        // Limpa o crash simulado para não deixar o provedor global reservado em outro teste.
        jdbc.update("update fila_envio_janela set reservado_por=null,reserva_ate=null where id='EMAIL'");
        jdbc.update("update comunicado_destinatario set status='FALHA',reservado_por=null,reserva_ate=null where comunicado_id=?",c.id());
    }
}
