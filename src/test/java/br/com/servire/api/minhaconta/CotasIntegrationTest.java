package br.com.servire.api.minhaconta;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.tenant.*;
import br.com.servire.api.integracao.*;
import br.com.servire.api.pessoa.*;
import br.com.servire.api.pessoa.dto.PessoaRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@AutoConfigureMockMvc
class CotasIntegrationTest extends AbstractIntegrationTest {
    @Autowired TenantRepository tenants; @Autowired DireitosLocaisRepository direitos;
    @Autowired PessoaService servico; @Autowired PessoaRepository pessoas; @Autowired CotasService cotas;
    @Autowired MockMvc mvc; @Autowired PlatformTransactionManager tx;
    @Autowired br.com.servire.api.acesso.PerfilService perfis;
    @Autowired br.com.servire.api.acesso.PerfilRepository perfilRepo;
    @Autowired br.com.servire.api.acesso.UsuarioParoquiaService usuarios;
    @Autowired br.com.servire.api.auth.UsuarioRepository usuarioRepo;
    @Autowired br.com.servire.api.inscricao.InscricaoService inscricoes;
    @Autowired br.com.servire.api.inscricao.InscricaoRepository inscricaoRepo;
    private UUID tenant;
    @BeforeEach void preparar() {
        String x = UUID.randomUUID().toString(); tenant = tenants.saveAndFlush(new Tenant(x, x, "Cotas", Tenant.Status.ATIVO)).getId();
        TenantContext.set(tenant);
    }
    @AfterEach void limpar() {TenantContext.clear();}
    private void limite(String json) {
        DireitosLocais d = direitos.findById(tenant).orElse(new DireitosLocais(tenant, UUID.randomUUID()));
        d.setLimites(json); d.setVersao(1); d.setSituacao("ATIVA"); d.setAcessoLiberado(true); d.setConfirmadoEm(Instant.now()); direitos.saveAndFlush(d);
    }
    private PessoaRequest responsavel(String nome) {
        return new PessoaRequest(Set.of(PessoaPapel.RESPONSAVEL), nome, null, null, null, null,
            List.of(), List.of(), List.of(), List.of(), null, null, null, null, null, null, null, null,
            null, null, null, null, null);
    }
    @Test void cadastroSemLimiteContinuaPermitidoELimiteZeroBloqueiaSemGravar() {
        servico.criar(responsavel("Existente")); limite("{\"pessoas\":0}");
        assertThatThrownBy(() -> servico.criar(responsavel("Bloqueada"))).isInstanceOf(CotaException.class);
        assertThat(pessoas.count()).isEqualTo(1);
    }
    @Test void planoReduzidoNaoImpedeEditarDadosExistentes() {
        Pessoa p = servico.criar(responsavel("Existente")); limite("{\"pessoas\":0}");
        assertThat(servico.atualizar(p.getId(), responsavel("Atualizada")).getNomeCompleto()).isEqualTo("Atualizada");
        assertThat(cotas.consumo().itens().getFirst().estado()).isEqualTo("EXCEDIDO");
    }
    @Test void ultimaVagaDoPlanoSoPermiteUmaDasCriacoesConcorrentes() throws Exception {
        limite("{\"pessoas\":1}");
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var inicio = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> criar = () -> {
                TenantContext.set(tenant);
                try {inicio.await(); servico.criar(responsavel(UUID.randomUUID().toString())); return true;}
                catch (CotaException ex) {return false;} finally {TenantContext.clear();}
            };
            var a = pool.submit(criar); var b = pool.submit(criar); inicio.countDown();
            assertThat(List.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS), b.get(15, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(pessoas.count()).isEqualTo(1);
    }
    @Test void consumoIsoladoPorParoquiaESemDadosPessoais() throws Exception {
        limite("{\"pessoas\":2}"); servico.criar(responsavel("Não expor nome"));
        var op = UsernamePasswordAuthenticationToken.authenticated("teste", null, List.of(new SimpleGrantedAuthority("PERM_PAROQUIA")));
        mvc.perform(get("/minha-conta/consumo").with(authentication(op)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].usado").value(1)).andExpect(jsonPath("$.itens[0].disponivel").value(1))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Não expor nome"))));
        String x = UUID.randomUUID().toString(); UUID outra = tenants.saveAndFlush(new Tenant(x, x, "Outra", Tenant.Status.ATIVO)).getId();
        TenantContext.set(outra); assertThat(cotas.consumo().itens().getFirst().usado()).isZero();
        var semPermissao = UsernamePasswordAuthenticationToken.authenticated("teste", null, List.of(new SimpleGrantedAuthority("PERM_ESCALA")));
        mvc.perform(get("/minha-conta/consumo").with(authentication(semPermissao))).andExpect(status().isForbidden());
    }
    @Test void formatoDeLimiteInvalidoNaoLiberaCrescimento() {
        limite("{\"pessoas\":-1}");
        assertThatThrownBy(() -> servico.criar(responsavel("Nova"))).isInstanceOf(CotaException.class).hasMessageContaining("corrigidos");
    }
    private PessoaRequest voluntario(List<br.com.servire.api.pessoa.dto.RelacaoRequest> relacoes) {
        var perfil=new br.com.servire.api.pessoa.dto.VoluntarioPerfilRequest(br.com.servire.api.voluntario.TipoVoluntario.ACOLITO,true,null,null,null,null,false,List.of(),null,null);
        return new PessoaRequest(Set.of(PessoaPapel.VOLUNTARIO, PessoaPapel.RESPONSAVEL), "Voluntário", null, null, null, null,
            List.of(), List.of(), relacoes, List.of(), null, null, null, null, null, null, null, null,
            null, null, null, null, perfil);
    }
    @Test void promoverPessoaParaVoluntarioTambemRespeitaCota() {
        var p=servico.criar(responsavel("Adulto")); limite("{\"pessoas\":1,\"voluntarios\":0}");
        assertThatThrownBy(() -> servico.atualizar(p.getId(),voluntario(List.of()))).isInstanceOf(CotaException.class);
        assertThat(cotas.consumo().itens().get(1).usado()).isZero();
        assertThat(pessoas.findById(p.getId()).orElseThrow().isVoluntario()).isFalse();
    }
    @Test void responsavelCriadoNaFichaNaoFicaOrfaoQuandoCotaEstoura() {
        limite("{\"pessoas\":1}");
        var r=new br.com.servire.api.pessoa.dto.RelacaoRequest(null,new br.com.servire.api.pessoa.dto.NovaPessoaRequest("Responsável",null,null),"Mãe","Filho",true);
        assertThatThrownBy(() -> servico.criar(voluntario(List.of(r)))).isInstanceOf(CotaException.class);
        assertThat(pessoas.count()).isZero();
    }
    @Test void conviteReservaVagaEReativacaoRespeitaLimiteSemCriarUsuarioOrfao() {
        perfis.criarPadrao(tenant);
        UUID perfil=perfilRepo.findByTenantIdAndNome(tenant,br.com.servire.api.acesso.PerfisPadrao.SECRETARIO).orElseThrow().getId();
        limite("{\"usuarios\":1}");
        String email=UUID.randomUUID()+"@cotas.test";
        var ativo=new br.com.servire.api.acesso.dto.UsuarioParoquiaRequest("Secretário",email,null,null,perfil,true);
        var u=usuarios.criar(ativo);
        assertThat(cotas.consumo().itens().get(2).usado()).isEqualTo(1);
        String outro=UUID.randomUUID()+"@cotas.test";
        assertThatThrownBy(() -> usuarios.criar(new br.com.servire.api.acesso.dto.UsuarioParoquiaRequest("Outro",outro,null,null,perfil,true))).isInstanceOf(CotaException.class);
        assertThat(usuarioRepo.findByEmail(outro)).isEmpty();
        usuarios.atualizar(u.usuarioId(),new br.com.servire.api.acesso.dto.UsuarioParoquiaRequest("Secretário",email,null,null,perfil,false));
        usuarios.criar(new br.com.servire.api.acesso.dto.UsuarioParoquiaRequest("Outro",outro,null,null,perfil,true));
        assertThatThrownBy(() -> usuarios.atualizar(u.usuarioId(),ativo)).isInstanceOf(CotaException.class);
        assertThat(usuarios.buscar(u.usuarioId()).ativo()).isFalse();
    }
    @Test void aprovarInscricaoSemVagaMantemPendenteESemPessoaCriada() {
        var i=inscricaoRepo.saveAndFlush(new br.com.servire.api.inscricao.Inscricao("Candidato"));
        var aprovador=usuarioRepo.saveAndFlush(new br.com.servire.api.auth.Usuario(UUID.randomUUID()+"@cotas.test","Aprovador"));
        limite("{\"voluntarios\":0}");
        assertThatThrownBy(() -> inscricoes.aprovar(i.getId(),aprovador.getId())).isInstanceOf(CotaException.class);
        assertThat(pessoas.count()).isZero();
        assertThat(inscricaoRepo.findById(i.getId()).orElseThrow().getStatus()).isEqualTo(br.com.servire.api.inscricao.StatusInscricao.PENDENTE);
    }
}
