package br.com.servire.api.financeiro;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.*;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static br.com.servire.api.financeiro.FinanceiroDtos.*;
import static br.com.servire.api.financeiro.MovimentoFinanceiro.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class FinanceiroIntegrationTest extends AbstractIntegrationTest {
    @Autowired FinanceiroService financeiro;
    @Autowired TenantRepository tenants;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private UUID tenant;
    private ContaResponse conta;
    /** Conta contábil de entrada (RECEITA) e de saída (DESPESA), cada uma dentro do seu grupo. */
    private CategoriaResponse categoria, categoriaSaida, grupoEntrada, grupoSaida;
    private final LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    @BeforeEach void preparar() {
        tenant = novaParoquia(); TenantContext.set(tenant);
        conta = financeiro.salvarConta(null,new ContaRequest("Caixa", new BigDecimal("100.00"), hoje.minusDays(10),true));
        grupoEntrada = financeiro.salvarCategoria(null,new CategoriaRequest("Receitas",true,Tipo.RECEITA,null));
        grupoSaida = financeiro.salvarCategoria(null,new CategoriaRequest("Despesas",true,Tipo.DESPESA,null));
        categoria = financeiro.salvarCategoria(null,new CategoriaRequest("Doações",true,Tipo.RECEITA,grupoEntrada.id()));
        categoriaSaida = financeiro.salvarCategoria(null,new CategoriaRequest("Energia",true,Tipo.DESPESA,grupoSaida.id()));
    }
    @AfterEach void limpar() { TenantContext.clear(); }
    private UUID novaParoquia() { String x=UUID.randomUUID().toString(); return tenants.saveAndFlush(new Tenant(x,x,"Financeiro teste",Tenant.Status.ATIVO)).getId(); }
    private MovimentoResponse movimento(Tipo tipo, String valor) {
        return financeiro.salvarMovimento(null,new MovimentoRequest("Teste",tipo,new BigDecimal(valor),hoje,conta.id(),(tipo==Tipo.RECEITA ? categoria : categoriaSaida).id(),null,0L));
    }
    @Test void saldoUsaSomenteBaixasEEstornoReverteUmaUnicaVez() {
        var entrada=movimento(Tipo.RECEITA,"50.00"); var saida=movimento(Tipo.DESPESA,"20.00");
        assertThat(financeiro.resumo(hoje,hoje).saldoTotal()).isEqualByComparingTo("100.00");
        entrada=financeiro.baixar(entrada.id(),new BaixaRequest(hoje,entrada.versao()));
        saida=financeiro.baixar(saida.id(),new BaixaRequest(hoje,saida.versao()));
        var resumo=financeiro.resumo(hoje,hoje);
        assertThat(resumo.receitas()).isEqualByComparingTo("50.00"); assertThat(resumo.despesas()).isEqualByComparingTo("20.00");
        assertThat(resumo.resultado()).isEqualByComparingTo("30.00"); assertThat(resumo.saldoTotal()).isEqualByComparingTo("130.00");
        financeiro.estornar(saida.id(),new VersaoRequest(saida.versao()));
        assertThat(financeiro.resumo(hoje,hoje).saldoTotal()).isEqualByComparingTo("150.00");
        var estornada=saida;
        assertThatThrownBy(() -> financeiro.estornar(estornada.id(),new VersaoRequest(estornada.versao()))).isInstanceOf(ConflictException.class);
    }
    @Test void periodoUsaDataPagamentoEnquantoListaUsaVencimento() {
        var m=movimento(Tipo.RECEITA,"30"); financeiro.baixar(m.id(),new BaixaRequest(hoje.minusDays(1),m.versao()));
        assertThat(financeiro.resumo(hoje,hoje).receitas()).isEqualByComparingTo("0");
        assertThat(financeiro.resumo(hoje,hoje).saldoTotal()).isEqualByComparingTo("130");
        assertThat(financeiro.listar(hoje,hoje,null,null,null,null,null,0,30).total()).isEqualTo(1);
    }
    @Test void tenantNaoVeNemAlteraMovimentosEContasDeOutraParoquia() {
        var m=movimento(Tipo.RECEITA,"50"); TenantContext.set(novaParoquia());
        assertThat(financeiro.contas()).isEmpty(); assertThat(financeiro.categorias()).isEmpty();
        assertThat(financeiro.listar(hoje,hoje,null,null,null,null,null,0,30).itens()).isEmpty();
        assertThat(financeiro.resumo(hoje,hoje).saldoTotal()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> financeiro.baixar(m.id(),new BaixaRequest(hoje,0L))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> financeiro.salvarMovimento(null,new MovimentoRequest("Invasão",Tipo.RECEITA,BigDecimal.ONE,hoje,conta.id(),categoria.id(),null,0L))).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void bancoImpedeReferenciaCruzadaMesmoSeAplicacaoErrar() {
        var m=movimento(Tipo.RECEITA,"1"); var outro=novaParoquia(); TenantContext.set(outro);
        var outra=financeiro.salvarConta(null,new ContaRequest("Outra",BigDecimal.ZERO,hoje,true));
        assertThatThrownBy(() -> jdbc.update("update financeiro_movimento set conta_id=? where id=?",outra.id(),m.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void saldoInicialProtegidoAceitaMesmoValorComEscalaDiferente() {
        movimento(Tipo.RECEITA,"1");
        financeiro.salvarConta(conta.id(),new ContaRequest("Caixa renomeado",new BigDecimal("100"),conta.dataSaldoInicial(),true));
        assertThatThrownBy(() -> financeiro.salvarConta(conta.id(),new ContaRequest("Caixa",new BigDecimal("200"),conta.dataSaldoInicial(),true))).isInstanceOf(ConflictException.class);
    }
    @Test void canceladoNaoPodeReceberBaixaENaoAlteraSaldo() {
        var m=movimento(Tipo.DESPESA,"10"); var c=financeiro.cancelar(m.id(),new VersaoRequest(m.versao()));
        assertThatThrownBy(() -> financeiro.baixar(c.id(),new BaixaRequest(hoje,c.versao()))).isInstanceOf(ConflictException.class);
        assertThat(financeiro.resumo(hoje,hoje).saldoTotal()).isEqualByComparingTo("100");
    }
    @Test void duasBaixasSimultaneasContamValorUmaUnicaVez() throws Exception {
        var m=movimento(Tipo.RECEITA,"50"); var inicio=new CountDownLatch(1);
        try (var executor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> tarefa=() -> {
                TenantContext.set(tenant);
                try { inicio.await(5,TimeUnit.SECONDS); financeiro.baixar(m.id(),new BaixaRequest(hoje,m.versao())); return true; }
                catch (ConflictException ex) { return false; } finally { TenantContext.clear(); }
            };
            var a=executor.submit(tarefa); var b=executor.submit(tarefa); inicio.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(financeiro.resumo(hoje,hoje).saldoTotal()).isEqualByComparingTo("150");
    }
    private UsernamePasswordAuthenticationToken usuario(String... permissoes) {
        return UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(UUID.randomUUID(),tenant,UsuarioTenant.Role.ADMIN,false),null,
            Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toList());
    }
    @Test void somenteLeituraNaoPodeBaixarOuConfigurar() throws Exception {
        var m=movimento(Tipo.RECEITA,"1");
        mvc.perform(get("/financeiro/contas").with(authentication(usuario("PERM_FINANCEIRO")))).andExpect(status().isOk());
        TenantContext.set(tenant);
        mvc.perform(post("/financeiro/movimentos/"+m.id()+"/baixar").with(authentication(usuario("PERM_FINANCEIRO"))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"dataPagamento\":\""+hoje+"\",\"versao\":0}")).andExpect(status().isForbidden());
        TenantContext.set(tenant);
        mvc.perform(post("/financeiro/contas").with(authentication(usuario("PERM_FINANCEIRO"))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nome\":\"Conta\",\"saldoInicial\":0,\"dataSaldoInicial\":\""+hoje+"\",\"ativo\":true}")).andExpect(status().isForbidden());
    }
    @Test void valorNegativoOuFracionadoDemaisRejeitadoPelaApi() throws Exception {
        for (String valor : List.of("-1","0","1.001")) {
            TenantContext.set(tenant);
            mvc.perform(post("/financeiro/movimentos").with(authentication(usuario("PERM_FINANCEIRO_CRIAR"))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"descricao":"Inválido","tipo":"RECEITA","valor":%s,"vencimento":"%s","contaId":"%s","categoriaId":"%s","versao":0}
                    """.formatted(valor,hoje,conta.id(),categoria.id()))).andExpect(status().isBadRequest());
        }
    }
    private CategoriaRequest grupo(String nome, Tipo tipo) { return new CategoriaRequest(nome,true,tipo,null); }
    private CategoriaRequest contaContabil(String nome, Tipo tipo, UUID grupoId) { return new CategoriaRequest(nome,true,tipo,grupoId); }
    private MovimentoRequest lancamento(Tipo tipo, UUID categoriaId) {
        return new MovimentoRequest("Teste",tipo,BigDecimal.TEN,hoje,conta.id(),categoriaId,null,0L);
    }
    @Test void contaContabilPrecisaDeGrupoDoMesmoTipoEGrupoNaoTemGrupo() {
        assertThatThrownBy(() -> financeiro.salvarCategoria(null,contaContabil("Conta errada",Tipo.RECEITA,grupoSaida.id()))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(null,contaContabil("Dentro de conta",Tipo.RECEITA,categoria.id()))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(null,contaContabil("Fantasma",Tipo.RECEITA,UUID.randomUUID()))).isInstanceOf(ResourceNotFoundException.class);
        var lista=financeiro.categorias();
        assertThat(lista).filteredOn(CategoriaResponse::ehGrupo).extracting(CategoriaResponse::nome).containsExactlyInAnyOrder("Receitas","Despesas");
        assertThat(lista).filteredOn(c -> !c.ehGrupo()).extracting(CategoriaResponse::nome).containsExactlyInAnyOrder("Doações","Energia");
    }
    @Test void nomesUnicosPorGrupoENoMesmoTipoDeGrupo() {
        assertThatThrownBy(() -> financeiro.salvarCategoria(null,grupo("receitas",Tipo.RECEITA))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(null,contaContabil("DOAÇÕES",Tipo.RECEITA,grupoEntrada.id()))).isInstanceOf(ConflictException.class);
        assertThatCode(() -> financeiro.salvarCategoria(null,grupo("Receitas",Tipo.DESPESA))).doesNotThrowAnyException();
        var outroGrupo=financeiro.salvarCategoria(null,grupo("Eventos",Tipo.RECEITA));
        assertThatCode(() -> financeiro.salvarCategoria(null,contaContabil("Doações",Tipo.RECEITA,outroGrupo.id()))).doesNotThrowAnyException();
        assertThatCode(() -> financeiro.salvarCategoria(categoria.id(),contaContabil("Doações",Tipo.RECEITA,grupoEntrada.id()))).doesNotThrowAnyException();
    }
    @Test void lancamentoSoEmContaContabilDoMesmoTipoEAtiva() {
        assertThatThrownBy(() -> financeiro.salvarMovimento(null,lancamento(Tipo.RECEITA,grupoEntrada.id()))).isInstanceOf(BadRequestException.class).hasMessageContaining("conta contábil");
        assertThatThrownBy(() -> financeiro.salvarMovimento(null,lancamento(Tipo.DESPESA,categoria.id()))).isInstanceOf(BadRequestException.class).hasMessageContaining("entradas");
        assertThatCode(() -> financeiro.salvarMovimento(null,lancamento(Tipo.DESPESA,categoriaSaida.id()))).doesNotThrowAnyException();
        financeiro.salvarCategoria(grupoSaida.id(),new CategoriaRequest("Despesas",false,Tipo.DESPESA,null));
        assertThatThrownBy(() -> financeiro.salvarMovimento(null,lancamento(Tipo.DESPESA,categoriaSaida.id()))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(null,contaContabil("Nova",Tipo.DESPESA,grupoSaida.id()))).isInstanceOf(BadRequestException.class);
    }
    @Test void estruturaComUsoNaoMudaDeTipoNemDeNivel() {
        movimento(Tipo.RECEITA,"5");
        assertThatThrownBy(() -> financeiro.salvarCategoria(categoria.id(),contaContabil("Doações",Tipo.DESPESA,grupoSaida.id()))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(categoria.id(),grupo("Doações",Tipo.RECEITA))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(grupoEntrada.id(),contaContabil("Receitas",Tipo.RECEITA,grupoEntrada.id()))).isInstanceOf(ConflictException.class);
        var outro=financeiro.salvarCategoria(null,grupo("Outro grupo",Tipo.RECEITA));
        assertThatThrownBy(() -> financeiro.salvarCategoria(grupoEntrada.id(),contaContabil("Receitas",Tipo.RECEITA,outro.id()))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(grupoEntrada.id(),new CategoriaRequest("Receitas",true,Tipo.DESPESA,null))).isInstanceOf(ConflictException.class);
        assertThatCode(() -> financeiro.salvarCategoria(categoria.id(),contaContabil("Doações",Tipo.RECEITA,outro.id()))).doesNotThrowAnyException();
    }
    @Test void bancoRejeitaGrupoDeOutraParoquiaEAutoReferencia() {
        var outra=novaParoquia(); TenantContext.set(outra);
        var grupoDeFora=financeiro.salvarCategoria(null,grupo("Grupo de fora",Tipo.RECEITA));
        TenantContext.set(tenant);
        assertThatThrownBy(() -> jdbc.update("update financeiro_categoria set grupo_id=? where id=?",grupoDeFora.id(),categoria.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update financeiro_categoria set grupo_id=id where id=?",categoria.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void tabelasNaoExpostasAoClienteSupabase() {
        for (String tabela : List.of("financeiro_conta","financeiro_categoria","financeiro_movimento")) {
            assertThat(jdbc.queryForObject("select relrowsecurity from pg_class where oid=?::regclass",Boolean.class,tabela)).isTrue();
            assertThat(jdbc.queryForObject("select has_table_privilege('anon',?,'SELECT')",Boolean.class,tabela)).isFalse();
            assertThat(jdbc.queryForObject("select has_table_privilege('authenticated',?,'INSERT')",Boolean.class,tabela)).isFalse();
        }
    }
}
