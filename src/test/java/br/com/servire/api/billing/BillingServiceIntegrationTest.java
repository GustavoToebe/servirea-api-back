package br.com.servire.api.billing;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.backoffice.BackofficeLogService;
import br.com.servire.api.backoffice.BackofficeParoquiaService;
import br.com.servire.api.backoffice.dto.CriarParoquiaRequest;
import br.com.servire.api.backoffice.dto.DashboardResponse;
import br.com.servire.api.backoffice.dto.FiltroParoquia;
import br.com.servire.api.billing.dto.CobrancaResponse;
import br.com.servire.api.billing.dto.CriarAssinaturaRequest;
import br.com.servire.api.billing.dto.FinanceiroResponse;
import br.com.servire.api.billing.dto.NovoPrecoRequest;
import br.com.servire.api.billing.dto.PlanoRequest;
import br.com.servire.api.billing.dto.PlanoResponse;
import br.com.servire.api.billing.dto.RegistrarPagamentoRequest;
import br.com.servire.api.billing.dto.SituacaoFinanceira;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Financeiro manual (V029). As tabelas de billing são globais: cada teste
 * cria a PRÓPRIA paróquia e o PRÓPRIO plano (código aleatório), senão o
 * unique de preço por data colidiria entre testes. Datas relativas a
 * {@link BillingService#hoje()} (fuso do Brasil) — o teste vale em
 * qualquer dia em que rodar.
 */
class BillingServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "SenhaForte123!";
    private static final BigDecimal CEM = new BigDecimal("100.00");

    @Autowired
    private BillingService billingService;

    @Autowired
    private PlanoService planoService;

    @Autowired
    private BackofficeParoquiaService backofficeParoquiaService;

    @Autowired
    private BackofficeLogService backofficeLogService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private LocalDate hoje;
    private Tenant paroquia;
    private PlanoResponse plano;

    @BeforeEach
    void preparar() {
        autenticarOperador();
        hoje = billingService.hoje();
        paroquia = novaParoquia();
        plano = novoPlano();
    }

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assinaturaMensalGeraUmaCobrancaPorMesDesdeOInicioAteOProximoMes() {
        LocalDate inicio = hoje.minusMonths(2).withDayOfMonth(1);

        FinanceiroResponse financeiro = assinar(Periodicidade.MENSAL, CEM, 10, inicio);

        assertThat(financeiro.cobrancas()).extracting(CobrancaResponse::competenciaInicio)
                .containsExactlyInAnyOrder(inicio, inicio.plusMonths(1), inicio.plusMonths(2), inicio.plusMonths(3));
        CobrancaResponse primeira = maisAntiga(financeiro);
        assertThat(primeira.competenciaFim()).isEqualTo(inicio.plusMonths(1).minusDays(1));
        assertThat(primeira.vencimento()).isEqualTo(inicio.withDayOfMonth(10));
        assertThat(primeira.valor()).isEqualByComparingTo(CEM);
        assertThat(primeira.vencida()).isTrue();
        assertThat(financeiro.assinaturaAtual().planoNome()).isEqualTo(plano.nome());
    }

    @Test
    void assinaturaAnualGeraUmaCobrancaPorPeriodoDeDozeMeses() {
        LocalDate inicio = hoje.minusMonths(13);

        FinanceiroResponse financeiro = assinar(Periodicidade.ANUAL, new BigDecimal("1000.00"), 5, inicio);

        assertThat(financeiro.cobrancas()).extracting(CobrancaResponse::competenciaInicio)
                .containsExactlyInAnyOrder(inicio, inicio.plusMonths(12));
        assertThat(maisAntiga(financeiro).competenciaFim()).isEqualTo(inicio.plusMonths(12).minusDays(1));
    }

    @Test
    void mensalComInicioDepoisDoDiaDeVencimentoVenceNoProprioInicio() {
        LocalDate inicio = hoje.minusMonths(1).withDayOfMonth(20);

        FinanceiroResponse financeiro = assinar(Periodicidade.MENSAL, CEM, 5, inicio);

        assertThat(maisAntiga(financeiro).vencimento()).isEqualTo(inicio);
    }

    @Test
    void abrirOFinanceiroDuasVezesNaoDuplicaCobrancas() {
        assinar(Periodicidade.MENSAL, CEM, 10, hoje.minusMonths(3).withDayOfMonth(1));
        int antes = billingService.financeiro(paroquia.getId()).cobrancas().size();

        billingService.financeiro(paroquia.getId());
        billingService.gerarCobrancasDeTodas();

        assertThat(billingService.financeiro(paroquia.getId()).cobrancas()).hasSize(antes);
    }

    @Test
    void valorOmitidoUsaOPrecoVigenteMaisRecenteQueJaComecou() {
        planoService.adicionarPreco(plano.id(), new NovoPrecoRequest(Periodicidade.MENSAL, CEM, hoje.minusDays(30)));
        planoService.adicionarPreco(plano.id(), new NovoPrecoRequest(
                Periodicidade.MENSAL, new BigDecimal("150.00"), hoje.minusDays(1)));
        planoService.adicionarPreco(plano.id(), new NovoPrecoRequest(
                Periodicidade.MENSAL, new BigDecimal("200.00"), hoje.plusDays(10)));

        FinanceiroResponse financeiro = assinar(Periodicidade.MENSAL, null, 10, hoje.withDayOfMonth(1));

        assertThat(financeiro.assinaturaAtual().valor()).isEqualByComparingTo("150.00");
        assertThat(planoService.listar()).filteredOn(p -> p.id().equals(plano.id()))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.precoMensal()).isEqualByComparingTo("150.00");
                    assertThat(p.precoAnual()).isNull();
                    assertThat(p.precos()).hasSize(3);
                });
    }

    @Test
    void semPrecoNoCatalogoESemValorInformadoLancaBadRequest() {
        assertThatThrownBy(() -> assinar(Periodicidade.ANUAL, null, 10, hoje))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void precoDuplicadoNaMesmaDataLancaConflict() {
        planoService.adicionarPreco(plano.id(), new NovoPrecoRequest(Periodicidade.MENSAL, CEM, hoje));

        assertThatThrownBy(() -> planoService.adicionarPreco(plano.id(),
                new NovoPrecoRequest(Periodicidade.MENSAL, new BigDecimal("120.00"), hoje)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void registrarPagamentoMarcaPagaAtivaTrialEEstendeVigencia() {
        LocalDate proximoMes = hoje.plusMonths(1).withDayOfMonth(1);
        CobrancaResponse cobranca = maisAntiga(assinar(Periodicidade.MENSAL, CEM, 10, proximoMes));

        FinanceiroResponse depois = billingService.registrarPagamento(paroquia.getId(), new RegistrarPagamentoRequest(
                List.of(cobranca.id()), hoje, FormaPagamento.PIX, null, "PIX conferido"));

        CobrancaResponse paga = porId(depois, cobranca.id());
        assertThat(paga.status()).isEqualTo(Cobranca.Status.PAGA);
        assertThat(paga.formaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(paga.pagoEm()).isEqualTo(hoje);
        assertThat(paga.valorPago()).isEqualByComparingTo(CEM);
        assertThat(paga.observacao()).isEqualTo("PIX conferido");
        assertThat(depois.resumo().totalPagoNoAno()).isEqualByComparingTo(CEM);

        Tenant atualizado = tenantRepository.findById(paroquia.getId()).orElseThrow();
        assertThat(atualizado.getStatus()).isEqualTo(Tenant.Status.ATIVO);
        assertThat(atualizado.getVigenciaAte()).isEqualTo(cobranca.competenciaFim());
        assertThat(atualizado.getUltimoPagamentoEm()).isNotNull();
        assertThat(backofficeLogService.listar())
                .anyMatch(l -> "PAGAMENTO_REGISTRAR".equals(l.getAcao()) && cobranca.id().equals(l.getEntidadeId()));
    }

    @Test
    void pagamentoParcialMantemBloqueadaEQuitarTudoLibera() {
        FinanceiroResponse financeiro = assinar(Periodicidade.MENSAL, CEM, 1, hoje.minusMonths(3).withDayOfMonth(1));
        backofficeParoquiaService.bloquear(paroquia.getId());
        List<CobrancaResponse> vencidas = financeiro.cobrancas().stream()
                .filter(CobrancaResponse::vencida)
                .sorted(Comparator.comparing(CobrancaResponse::competenciaInicio))
                .toList();
        assertThat(vencidas).hasSizeGreaterThanOrEqualTo(3);
        assertThat(financeiro.resumo().diasAtraso()).isPositive();

        billingService.registrarPagamento(paroquia.getId(), new RegistrarPagamentoRequest(
                List.of(vencidas.getFirst().id()), hoje, FormaPagamento.DINHEIRO, null, null));
        assertThat(tenantRepository.findById(paroquia.getId()).orElseThrow().getStatus())
                .isEqualTo(Tenant.Status.BLOQUEADO);

        FinanceiroResponse quitado = billingService.registrarPagamento(paroquia.getId(), new RegistrarPagamentoRequest(
                vencidas.subList(1, vencidas.size()).stream().map(CobrancaResponse::id).toList(),
                hoje, FormaPagamento.CARTAO_CREDITO, null, null));

        assertThat(quitado.resumo().vencidas()).isZero();
        assertThat(quitado.resumo().diasAtraso()).isZero();
        assertThat(tenantRepository.findById(paroquia.getId()).orElseThrow().getStatus())
                .isEqualTo(Tenant.Status.ATIVO);
    }

    @Test
    void valorPagoInformadoComMaisDeUmaCobrancaLancaBadRequest() {
        FinanceiroResponse financeiro = assinar(Periodicidade.MENSAL, CEM, 10, hoje.minusMonths(1).withDayOfMonth(1));
        List<UUID> ids = financeiro.cobrancas().stream().map(CobrancaResponse::id).toList();

        assertThatThrownBy(() -> billingService.registrarPagamento(paroquia.getId(), new RegistrarPagamentoRequest(
                ids, hoje, FormaPagamento.PIX, new BigDecimal("50.00"), null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void pagamentoComDataNoFuturoLancaBadRequest() {
        CobrancaResponse cobranca = maisAntiga(assinar(Periodicidade.MENSAL, CEM, 10, hoje.withDayOfMonth(1)));

        assertThatThrownBy(() -> billingService.registrarPagamento(paroquia.getId(), new RegistrarPagamentoRequest(
                List.of(cobranca.id()), hoje.plusDays(1), FormaPagamento.PIX, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void pagarCobrancaJaPagaOuIsentaLancaBadRequest() {
        FinanceiroResponse financeiro = assinar(Periodicidade.MENSAL, CEM, 10, hoje.minusMonths(1).withDayOfMonth(1));
        CobrancaResponse primeira = maisAntiga(financeiro);
        CobrancaResponse outra = financeiro.cobrancas().stream()
                .filter(c -> !c.id().equals(primeira.id())).findFirst().orElseThrow();
        pagar(primeira.id());
        billingService.isentar(paroquia.getId(), outra.id(), "Cortesia");

        assertThatThrownBy(() -> pagar(primeira.id())).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> pagar(outra.id())).isInstanceOf(BadRequestException.class);
        assertThat(porId(billingService.financeiro(paroquia.getId()), outra.id()).observacao())
                .isEqualTo("Cortesia");
    }

    @Test
    void cobrancaDeOutraParoquiaDaNotFound() {
        CobrancaResponse cobranca = maisAntiga(assinar(Periodicidade.MENSAL, CEM, 10, hoje.withDayOfMonth(1)));
        Tenant outra = novaParoquia();

        assertThatThrownBy(() -> billingService.registrarPagamento(outra.getId(), new RegistrarPagamentoRequest(
                List.of(cobranca.id()), hoje, FormaPagamento.PIX, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> billingService.estornar(outra.getId(), cobranca.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void estornarVoltaACobrancaParaAberta() {
        CobrancaResponse cobranca = maisAntiga(assinar(Periodicidade.MENSAL, CEM, 10, hoje.withDayOfMonth(1)));
        pagar(cobranca.id());

        FinanceiroResponse depois = billingService.estornar(paroquia.getId(), cobranca.id());

        CobrancaResponse estornada = porId(depois, cobranca.id());
        assertThat(estornada.status()).isEqualTo(Cobranca.Status.ABERTA);
        assertThat(estornada.pagoEm()).isNull();
        assertThat(estornada.formaPagamento()).isNull();
        assertThatThrownBy(() -> billingService.estornar(paroquia.getId(), cobranca.id()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void trocarDePlanoEncerraAAnteriorECancelaSoAsAbertasDaquiPraFrente() {
        LocalDate inicio = hoje.minusMonths(2).withDayOfMonth(1);
        FinanceiroResponse antes = assinar(Periodicidade.MENSAL, CEM, 10, inicio);
        UUID assinaturaAntiga = antes.assinaturaAtual().id();
        pagar(maisAntiga(antes).id());
        LocalDate proximoMes = hoje.plusMonths(1).withDayOfMonth(1);
        PlanoResponse pro = novoPlano();

        FinanceiroResponse depois = billingService.criarAssinatura(paroquia.getId(), new CriarAssinaturaRequest(
                pro.id(), Periodicidade.MENSAL, new BigDecimal("180.00"), 10, proximoMes, null));

        assertThat(depois.assinaturaAtual().planoId()).isEqualTo(pro.id());
        assertThat(depois.assinaturas()).hasSize(2);
        List<CobrancaResponse> daAntiga = depois.cobrancas().stream()
                .filter(c -> c.assinaturaId().equals(assinaturaAntiga)).toList();
        assertThat(daAntiga).filteredOn(c -> c.competenciaInicio().equals(inicio))
                .extracting(CobrancaResponse::status).containsExactly(Cobranca.Status.PAGA);
        assertThat(daAntiga).filteredOn(c -> c.competenciaInicio().equals(hoje.withDayOfMonth(1)))
                .extracting(CobrancaResponse::status).containsExactly(Cobranca.Status.ABERTA);
        assertThat(daAntiga).filteredOn(c -> c.competenciaInicio().equals(proximoMes))
                .extracting(CobrancaResponse::status).containsExactly(Cobranca.Status.CANCELADA);
        assertThat(depois.cobrancas()).filteredOn(c -> c.assinaturaId().equals(depois.assinaturaAtual().id()))
                .singleElement()
                .satisfies(c -> assertThat(c.valor()).isEqualByComparingTo("180.00"));
    }

    @Test
    void paroquiaCanceladaNaoRecebeAssinatura() {
        Tenant cancelada = tenantRepository.findById(paroquia.getId()).orElseThrow();
        cancelada.setStatus(Tenant.Status.CANCELADO);
        tenantRepository.saveAndFlush(cancelada);

        assertThatThrownBy(() -> assinar(Periodicidade.MENSAL, CEM, 10, hoje))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void gerarAdiantadasCriaOsMesesPedidosERespeitaOLimite() {
        assinar(Periodicidade.MENSAL, CEM, 10, hoje.withDayOfMonth(1));
        YearMonth ate = YearMonth.from(hoje).plusMonths(5);

        FinanceiroResponse financeiro = billingService.gerarAdiantadas(paroquia.getId(), ate);

        assertThat(financeiro.cobrancas()).hasSize(6);
        assertThatThrownBy(() -> billingService.gerarAdiantadas(
                paroquia.getId(), YearMonth.from(hoje).plusMonths(BillingService.MAX_MESES_ADIANTADOS + 1)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listagemFiltraEmAtrasoEMostraPlanoEDiasDeAtraso() {
        DashboardResponse antes = backofficeParoquiaService.dashboard();
        assinar(Periodicidade.MENSAL, CEM, 1, hoje.minusMonths(2).withDayOfMonth(1));
        Tenant emDia = novaParoquia();

        List<Tenant> emAtraso = backofficeParoquiaService.listar(new FiltroParoquia(
                null, null, null, null, null, null, null, null, null, null, true));
        assertThat(emAtraso).extracting(Tenant::getId).contains(paroquia.getId()).doesNotContain(emDia.getId());

        Map<UUID, SituacaoFinanceira> situacao = billingService.situacaoPorTenant(
                List.of(paroquia.getId(), emDia.getId()));
        assertThat(situacao.get(paroquia.getId()).planoNome()).isEqualTo(plano.nome());
        assertThat(situacao.get(paroquia.getId()).cobrancasVencidas()).isGreaterThanOrEqualTo(2);
        assertThat(situacao.get(paroquia.getId()).diasAtraso()).isPositive();
        assertThat(situacao).doesNotContainKey(emDia.getId());

        DashboardResponse depois = backofficeParoquiaService.dashboard();
        assertThat(depois.emAtraso()).isEqualTo(antes.emAtraso() + 1);
    }

    @Test
    void dashboardSomaORecebidoNoMes() {
        BigDecimal antes = backofficeParoquiaService.dashboard().recebidoNoMes();
        CobrancaResponse cobranca = maisAntiga(assinar(Periodicidade.MENSAL, CEM, 10, hoje.withDayOfMonth(1)));

        billingService.registrarPagamento(paroquia.getId(), new RegistrarPagamentoRequest(
                List.of(cobranca.id()), hoje, FormaPagamento.PIX, new BigDecimal("90.00"), "desconto"));

        assertThat(backofficeParoquiaService.dashboard().recebidoNoMes())
                .isEqualByComparingTo(antes.add(new BigDecimal("90.00")));
    }

    private FinanceiroResponse assinar(Periodicidade periodicidade, BigDecimal valor, int dia, LocalDate inicio) {
        return billingService.criarAssinatura(paroquia.getId(), new CriarAssinaturaRequest(
                plano.id(), periodicidade, valor, dia, inicio, null));
    }

    private void pagar(UUID cobrancaId) {
        billingService.registrarPagamento(paroquia.getId(), new RegistrarPagamentoRequest(
                List.of(cobrancaId), hoje, FormaPagamento.PIX, null, null));
    }

    private static CobrancaResponse maisAntiga(FinanceiroResponse financeiro) {
        return financeiro.cobrancas().stream()
                .min(Comparator.comparing(CobrancaResponse::competenciaInicio)).orElseThrow();
    }

    private static CobrancaResponse porId(FinanceiroResponse financeiro, UUID id) {
        return financeiro.cobrancas().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private PlanoResponse novoPlano() {
        String codigo = "T" + UUID.randomUUID().toString().substring(0, 8);
        return planoService.criar(new PlanoRequest(codigo, "Plano " + codigo, null, true));
    }

    private Tenant novaParoquia() {
        String sufixo = "fin-" + UUID.randomUUID().toString().substring(0, 8);
        return backofficeParoquiaService.criar(new CriarParoquiaRequest(
                "COD-" + sufixo, sufixo, "Paróquia " + sufixo,
                null, null, null, null, null, null, null, null, null, null, null, null,
                new CriarParoquiaRequest.AdminInicial("Padre " + sufixo, sufixo + "@teste.com", SENHA)));
    }

    private void autenticarOperador() {
        Usuario operador = new Usuario("op-" + UUID.randomUUID() + "@teste.com", "Operador teste");
        operador.setOperadorSaas(true);
        operador = usuarioRepository.saveAndFlush(operador);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.backoffice(operador.getId()),
                null,
                List.of(new SimpleGrantedAuthority("PERM_BACKOFFICE"))));
    }
}
