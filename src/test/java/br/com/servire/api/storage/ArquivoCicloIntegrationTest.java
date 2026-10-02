package br.com.servire.api.storage;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.Pessoas;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.VoluntarioService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ArquivoCicloIntegrationTest extends AbstractIntegrationTest {
    @Autowired ArquivoCicloService ciclo;
    @Autowired ArquivoPendenciaJob job;
    @Autowired TenantRepository tenants;
    @Autowired PessoaRepository pessoas;
    @Autowired VoluntarioService voluntarios;
    @Autowired PlatformTransactionManager tm;
    @MockitoBean StorageService storage;
    @PersistenceContext EntityManager em;

    UUID tenant;
    TransactionTemplate tx;

    @BeforeEach
    void preparar() {
        String slug = UUID.randomUUID().toString();
        tenant = tenants.saveAndFlush(new Tenant(slug, slug, "Arquivos", Tenant.Status.ATIVO)).getId();
        TenantContext.set(tenant);
        tx = new TransactionTemplate(tm);
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    List<ArquivoPendencia> pendencias() {
        return tx.execute(s -> em.createQuery("select p from ArquivoPendencia p order by p.criadoEm", ArquivoPendencia.class).getResultList());
    }

    /** O job limpa o TenantContext ao terminar cada paróquia; a asserção seguinte precisa dele de volta. */
    int rodar() {
        int n = job.processarAgora();
        TenantContext.set(tenant);
        return n;
    }

    void vencer() {
        tx.executeWithoutResult(s -> em.createQuery("update ArquivoPendencia p set p.proximaTentativa=:t, p.criadoEm=:c")
                .setParameter("t", Instant.now().minusSeconds(5)).setParameter("c", Instant.now().minus(Duration.ofHours(7))).executeUpdate());
    }

    @Test
    void remocaoSoVaiParaOBancoSeATransacaoDeNegocioConfirmar() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            ciclo.agendarRemocao("a/foto-velha.png");
            throw new IllegalStateException("negócio falhou");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(pendencias()).isEmpty();
        verify(storage, never()).excluirConfirmando(anyString());
    }

    @Test
    void remocaoConfirmadaTentaLogoEFicaDuravelSeOProvedorFalhar() {
        when(storage.excluirConfirmando("a/foto-velha.png")).thenReturn(false);
        tx.executeWithoutResult(s -> ciclo.agendarRemocao("a/foto-velha.png"));
        verify(storage).excluirConfirmando("a/foto-velha.png");
        assertThat(pendencias()).singleElement().satisfies(p -> {
            assertThat(p.tipo).isEqualTo(ArquivoPendencia.Tipo.REMOCAO);
            assertThat(p.tentativas).isZero();
        });
        // O job repete com sucesso e a pendência some.
        vencer();
        when(storage.excluirConfirmando("a/foto-velha.png")).thenReturn(true);
        assertThat(rodar()).isGreaterThanOrEqualTo(1);
        assertThat(pendencias()).isEmpty();
    }

    @Test
    void falhaRepetidaAumentaTentativasEEsperaSemApagarARegistroNemTravarReserva() {
        when(storage.excluirConfirmando(anyString())).thenReturn(false);
        tx.executeWithoutResult(s -> ciclo.agendarRemocao("b/x.png"));
        vencer();
        rodar();
        var p = pendencias().getFirst();
        assertThat(p.tentativas).isEqualTo(1);
        assertThat(p.proximaTentativa).isAfter(Instant.now().plusSeconds(60));
        assertThat(p.reservadoPor).isNull();
        assertThat(p.erro).isNotBlank();
        // Ainda dentro da espera: o job não tenta de novo.
        clearInvocations(storage);
        rodar();
        verify(storage, never()).excluirConfirmando(anyString());
    }

    @Test
    void uploadSemReferenciaSobreviveAoRollbackEOJobLimpaDepoisDaCarencia() {
        ciclo.registrarUpload("c/orfa.png");
        assertThat(pendencias()).singleElement().satisfies(p -> assertThat(p.tipo).isEqualTo(ArquivoPendencia.Tipo.UPLOAD));
        // Dentro da carência a operação de negócio pode estar em andamento: nada é apagado.
        tx.executeWithoutResult(s -> em.createQuery("update ArquivoPendencia p set p.proximaTentativa=:t").setParameter("t", Instant.now().minusSeconds(5)).executeUpdate());
        rodar();
        verify(storage, never()).excluirConfirmando(anyString());
        assertThat(pendencias().getFirst().proximaTentativa).isAfter(Instant.now().plus(Duration.ofHours(1)));
        // Passada a carência e sem referência, o arquivo sai do bucket.
        vencer();
        when(storage.excluirConfirmando("c/orfa.png")).thenReturn(true);
        rodar();
        verify(storage).excluirConfirmando("c/orfa.png");
        assertThat(pendencias()).isEmpty();
    }

    @Test
    void caminhoAindaReferenciadoNuncaEApagado() {
        var v = Pessoas.voluntario("Com foto");
        v.getVoluntario().setFotoPath("d/em-uso.png");
        pessoas.saveAndFlush(v);
        ciclo.registrarUpload("d/em-uso.png");
        tx.executeWithoutResult(s -> ciclo.agendarRemocao("d/em-uso.png"));
        verify(storage, never()).excluirConfirmando("d/em-uso.png");
        vencer();
        rodar();
        verify(storage, never()).excluirConfirmando("d/em-uso.png");
        assertThat(pendencias()).isEmpty();
    }

    @Test
    void trocaDeFotoDeVoluntarioAgendaARemocaoDaAnterior() {
        var criado = Pessoas.persistirVoluntario(pessoas, "Troca");
        when(storage.armazenar(anyString(), any(byte[].class), anyString())).thenAnswer(i -> i.getArgument(0));
        when(storage.excluirConfirmando(anyString())).thenReturn(true);
        var png = new MockMultipartFile("foto", "a.png", "image/png", new byte[]{1, 2, 3});
        String primeira = voluntarios.definirFoto(criado.getId(), png).getFotoPath();
        assertThat(pendencias()).isEmpty();
        voluntarios.definirFoto(criado.getId(), png);
        verify(storage).excluirConfirmando(primeira);
        assertThat(pendencias()).isEmpty();
    }

    @Test
    void erroNaGravacaoDepoisDoUploadDescartaOArquivoEnviado() {
        when(storage.armazenar(anyString(), any(byte[].class), anyString())).thenAnswer(i -> i.getArgument(0));
        when(storage.excluirConfirmando(anyString())).thenReturn(true);
        var png = new MockMultipartFile("foto", "a.png", "image/png", new byte[]{1, 2, 3});
        UUID inexistente = UUID.randomUUID();
        assertThatThrownBy(() -> voluntarios.definirFoto(inexistente, png)).isInstanceOf(RuntimeException.class);
        verify(storage, never()).armazenar(anyString(), any(), anyString()); // falha antes de enviar
        var criado = Pessoas.persistirVoluntario(pessoas, "Falha");
        doThrow(new StorageException("fora do ar", null)).when(storage).armazenar(anyString(), any(byte[].class), anyString());
        assertThatThrownBy(() -> voluntarios.definirFoto(criado.getId(), png)).isInstanceOf(StorageException.class);
        // Upload falhou no provedor: o registro de intenção fica para o job conferir depois da carência.
        assertThat(pendencias()).singleElement().satisfies(p -> assertThat(p.tipo).isEqualTo(ArquivoPendencia.Tipo.UPLOAD));
    }
}
