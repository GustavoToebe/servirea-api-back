package br.com.servire.api.minhaconta;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.tenant.*;
import br.com.servire.api.pessoa.*;
import br.com.servire.api.voluntario.*;
import br.com.servire.api.inscricao.*;
import br.com.servire.api.integracao.*;
import br.com.servire.api.storage.*;
import br.com.servire.api.comunicacao.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ArmazenamentoIntegrationTest extends AbstractIntegrationTest {
    @Autowired TenantRepository tenants; @Autowired PessoaRepository pessoas;
    @Autowired InscricaoRepository inscricoes; @Autowired VoluntarioService voluntarios;
    @Autowired DireitosLocaisRepository direitos; @Autowired CotasService cotas;
    @Autowired ReconciliadorArmazenamento reconciliador; @Autowired MockMvc mvc;
    @Autowired LayoutRepository layouts; @Autowired ComunicadoRepository comunicados;
    @Autowired ComunicadoAnexoRepository anexos;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
    @Autowired org.springframework.transaction.PlatformTransactionManager tx;
    @MockitoBean StorageService storage;
    private UUID tenant;
    @BeforeEach void preparar() {
        tenant=novaParoquia(); TenantContext.set(tenant);
        when(storage.armazenar(anyString(),any(),anyString())).thenAnswer(i -> i.getArgument(0));
    }
    @AfterEach void limpar() {TenantContext.clear();}
    UUID novaParoquia() {String x=UUID.randomUUID().toString();return tenants.saveAndFlush(new Tenant(x,x,"Armazenamento",Tenant.Status.ATIVO)).getId();}
    Pessoa foto(String caminho,Long tamanho) {
        var p=new Pessoa(Set.of(PessoaPapel.VOLUNTARIO),"Voluntário"); var v=new Voluntario(); v.setFotoPath(caminho); v.setFotoTamanhoBytes(tamanho); p.setVoluntario(v);
        return pessoas.saveAndFlush(p);
    }
    void limitar() {var d=new DireitosLocais(tenant,UUID.randomUUID());d.setLimites("{\"armazenamento_mb\":1}");d.setSituacao("ATIVA");d.setAcessoLiberado(true);d.setConfirmadoEm(java.time.Instant.now());direitos.saveAndFlush(d);}
    MockMultipartFile arquivo(int bytes) {return new MockMultipartFile("foto","foto.jpg","image/jpeg",new byte[bytes]);}
    CotasService.Item consumo() {return cotas.consumo().itens().stream().filter(i -> i.codigo().equals("armazenamento_mb")).findFirst().orElseThrow();}
    @Test void tamanhoExatoContaBytesESubstituicaoNaoDuplica() {
        var p=foto("perfil-antigo",1048576L);limitar();
        assertThat(consumo().estado()).isEqualTo("ATINGIDO");
        voluntarios.definirFoto(p.getId(),arquivo(800000));
        assertThat(consumo().usado()).isEqualTo(800000);
        assertThat(consumo().limite()).isEqualTo(1048576);
        assertThatThrownBy(() -> voluntarios.definirFoto(p.getId(),arquivo(1048577))).isInstanceOf(CotaException.class);
        assertThat(consumo().usado()).isEqualTo(800000);
    }
    @Test void fotoCompartilhadaComInscricaoContaUmaVezEMantemCreditoCorreto() {
        var p=foto("compartilhada",800000L); var i=new Inscricao("Aprovada"); i.setFotoPath("compartilhada");i.setFotoTamanhoBytes(800000L);inscricoes.saveAndFlush(i); limitar();
        assertThat(consumo().usado()).isEqualTo(800000);
        assertThatThrownBy(() -> voluntarios.definirFoto(p.getId(),arquivo(300000))).isInstanceOf(CotaException.class);
        verify(storage,never()).armazenar(anyString(),any(),anyString());
        voluntarios.definirFoto(p.getId(),arquivo(200000));assertThat(consumo().usado()).isEqualTo(1000000);
    }
    @Test void fotoAntigaDesconhecidaBloqueiaUploadLimitadoSemInventarZero() {
        var p=foto("legado",null);limitar();
        assertThat(consumo().estado()).isEqualTo("INVENTARIO_PENDENTE");assertThat(consumo().disponivel()).isNull();
        assertThatThrownBy(() -> voluntarios.definirFoto(p.getId(),arquivo(10))).isInstanceOf(CotaException.class).hasMessageContaining("fotos antigas");
        verify(storage,never()).armazenar(anyString(),any(),anyString());
        when(storage.tamanho("legado")).thenThrow(new StorageException("Indisponível",null));
        assertThat(reconciliador.conferir().falhas()).isEqualTo(1);assertThat(consumo().pendentes()).isEqualTo(1);
    }
    @Test void conferenciaForaDaTransacaoEIsoladaMesmoComCaminhoIgual() throws Exception {
        var p=foto("legado",null);UUID outro=novaParoquia();TenantContext.set(outro);var outra=foto("legado",null);TenantContext.set(tenant);
        when(storage.tamanho("legado")).thenAnswer(i -> {assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return 500000L;});
        mvc.perform(post("/minha-conta/armazenamento/conferir").with(user("leitor").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_PAROQUIA"))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/minha-conta/armazenamento/conferir").with(user("admin").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_PAROQUIA_ALTERAR"))).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.conferidos").value(1)).andExpect(jsonPath("$.pendentes").value(0));
        TenantContext.set(tenant);assertThat(consumo().usado()).isEqualTo(500000);
        TenantContext.set(outro);assertThat(consumo().pendentes()).isEqualTo(1);
        assertThat(pessoas.findById(outra.getId()).orElseThrow().getVoluntario().getFotoTamanhoBytes()).isNull();
    }
    @Test void doisUploadsConcorrentesNaoUltrapassamCota() throws Exception {
        when(storage.excluirConfirmando(anyString())).thenReturn(true);
        var a=foto(null,null);var b=foto(null,null);limitar();var inicio=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            java.util.function.Function<UUID,Callable<Boolean>> tarefa=id -> () -> {TenantContext.set(tenant);try {inicio.await();voluntarios.definirFoto(id,arquivo(800000));return true;}catch(CotaException ex){return false;}finally{TenantContext.clear();}};
            var x=pool.submit(tarefa.apply(a.getId()));var y=pool.submit(tarefa.apply(b.getId()));inicio.countDown();
            assertThat(List.of(x.get(15,TimeUnit.SECONDS),y.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(consumo().usado()).isEqualTo(800000); verify(storage,times(2)).armazenar(anyString(),any(),anyString());
        // O upload do perdedor não fica no bucket: foi descartado logo após a recusa por cota.
        verify(storage,times(1)).excluirConfirmando(anyString());
    }
    @Test void anexosLiberadosNaoContamMasMetadadosDoHistoricoPermanecem() {
        var l=layouts.saveAndFlush(new Layout("Teste",TipoLayout.TODOS,TipoEnvio.EMAIL,"Assunto","Conteúdo",true));
        var c=comunicados.saveAndFlush(new Comunicado(TipoEnvio.EMAIL,l,"Assunto",EnviarPara.PESSOA,null));
        var a=anexos.saveAndFlush(new ComunicadoAnexo(c.getId(),"documento.pdf","application/pdf",new byte[4096]));
        assertThat(consumo().usado()).isEqualTo(4096);
        // A rotina existente usa o mesmo update JPQL ao concluir o comunicado.
        new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(s -> em.createQuery("update ComunicadoAnexo a set a.conteudo=null where a.comunicadoId=:id").setParameter("id",c.getId()).executeUpdate());
        assertThat(consumo().usado()).isZero();assertThat(anexos.findById(a.getId()).orElseThrow().getTamanho()).isEqualTo(4096);
    }
    @Test void falhasNoPrimeiroLoteNaoImpedemConferirFotosSeguintes() {
        for(int i=0;i<6;i++) foto("legado-"+i,null);
        when(storage.tamanho(anyString())).thenThrow(new StorageException("Falha",null));
        var primeira=reconciliador.conferir();assertThat(primeira.proximoInicio()).isEqualTo(5);assertThat(primeira.falhas()).isEqualTo(5);
        doReturn(500L).when(storage).tamanho("legado-5");
        var segunda=reconciliador.conferir(primeira.proximoInicio());assertThat(segunda.conferidos()).isEqualTo(1);assertThat(segunda.pendentes()).isEqualTo(5);assertThat(segunda.proximoInicio()).isZero();
    }
}
