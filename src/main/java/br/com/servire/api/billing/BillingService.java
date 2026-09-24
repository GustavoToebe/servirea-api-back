package br.com.servire.api.billing;

import br.com.servire.api.backoffice.BackofficeLogService;
import br.com.servire.api.billing.dto.AssinaturaResponse;
import br.com.servire.api.billing.dto.CobrancaResponse;
import br.com.servire.api.billing.dto.CriarAssinaturaRequest;
import br.com.servire.api.billing.dto.FinanceiroResponse;
import br.com.servire.api.billing.dto.RegistrarPagamentoRequest;
import br.com.servire.api.billing.dto.SituacaoFinanceira;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Financeiro manual da paróquia no backoffice (V029; seções 62/63/112 e
 * 131.3 do plano mestre). Substitui o antigo "marcar como pago", que só
 * gravava {@code tenant.ultimo_pagamento_em} sem histórico.
 *
 * <p><b>Decisões (com o usuário, 23/09/2026):</b></p>
 * <ul>
 *   <li>Cobranças geradas por período: MENSAL = uma por mês de calendário
 *       a partir do mês do início; ANUAL = uma a cada 12 meses a partir do
 *       início. Vencimento = {@code dia_vencimento} do primeiro mês do
 *       período, nunca antes do início do contrato.</li>
 *   <li>Geração sob demanda (ao abrir o financeiro) + {@link BillingJob}
 *       diário, sempre até <b>hoje + 1 mês</b>: a próxima cobrança já
 *       aparece para ser paga adiantada. O unique
 *       {@code (assinatura_id, competencia_inicio)} torna isso idempotente.</li>
 *   <li>Bloqueio automático por atraso (3 dias, 131.3) NÃO existe: aqui só
 *       se calcula o atraso. Pagar tira TRIAL/BLOQUEADO para ATIVO quando
 *       não sobra nada vencido; bloquear continua sendo ação manual.</li>
 * </ul>
 *
 * <p>Tabelas globais, sem {@code TenantContext}: o {@code tenantId} vem do
 * path da rota {@code /admin/paroquias/{id}/...}, e toda cobrança é buscada
 * junto com esse tenant ({@code findByIdInAndTenantId}) — um id de cobrança
 * de outra paróquia dá 404, nunca altera a errada.</p>
 */
@Service
public class BillingService {

    /** Quanto à frente a cobrança é gerada sem ninguém pedir. */
    static final int MESES_ANTECEDENCIA = 1;

    /** Limite do "gerar adiantadas" — evita gerar décadas por erro de digitação. */
    static final int MAX_MESES_ADIANTADOS = 36;

    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MM/yyyy");

    private final AssinaturaRepository assinaturaRepository;
    private final CobrancaRepository cobrancaRepository;
    private final TenantRepository tenantRepository;
    private final PlanoService planoService;
    private final BackofficeLogService backofficeLogService;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public BillingService(AssinaturaRepository assinaturaRepository,
                          CobrancaRepository cobrancaRepository,
                          TenantRepository tenantRepository,
                          PlanoService planoService,
                          BackofficeLogService backofficeLogService,
                          Clock clock) {
        this.assinaturaRepository = assinaturaRepository;
        this.cobrancaRepository = cobrancaRepository;
        this.tenantRepository = tenantRepository;
        this.planoService = planoService;
        this.backofficeLogService = backofficeLogService;
        this.clock = clock;
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    @Transactional
    public FinanceiroResponse financeiro(UUID tenantId) {
        buscarTenant(tenantId);
        assinaturaRepository.findByTenantIdAndStatus(tenantId, Assinatura.Status.ATIVA)
                .ifPresent(a -> gerarCobrancas(a, hoje().plusMonths(MESES_ANTECEDENCIA)));
        return montarFinanceiro(tenantId);
    }

    /**
     * Contrata ou troca de plano. Na troca, a assinatura ATIVA é encerrada
     * na véspera do novo início e as cobranças ABERTAS a partir do novo
     * início são canceladas (as já pagas ficam como estão — o operador
     * estorna se precisar). Flush antes de inserir a nova: o índice único
     * parcial "uma ATIVA por tenant" é checado na hora do INSERT.
     */
    @Transactional
    public FinanceiroResponse criarAssinatura(UUID tenantId, CriarAssinaturaRequest request) {
        Tenant tenant = buscarTenant(tenantId);
        if (tenant.getStatus() == Tenant.Status.CANCELADO) {
            throw new BadRequestException("Paróquia cancelada não pode ter assinatura.");
        }
        Plano plano = planoService.buscar(request.planoId());
        if (!plano.isAtivo()) {
            throw new BadRequestException("Plano inativo não pode ser contratado.");
        }
        BigDecimal valor = request.valor() != null
                ? request.valor()
                : planoService.precoVigente(plano.getId(), request.periodicidade(), hoje())
                        .orElseThrow(() -> new BadRequestException(
                                "Plano sem preço cadastrado para esta periodicidade. Informe o valor."));

        assinaturaRepository.findByTenantIdAndStatus(tenantId, Assinatura.Status.ATIVA).ifPresent(atual -> {
            atual.encerrar(request.inicio().minusDays(1));
            cancelarAbertasAPartirDe(atual, request.inicio(), "Troca de plano");
            backofficeLogService.registrar("ASSINATURA_ENCERRAR", "ASSINATURA", atual.getId(), tenantId);
        });
        entityManager.flush();

        Assinatura nova = new Assinatura(tenantId, plano, request.periodicidade(), valor,
                request.diaVencimento(), request.inicio());
        nova.setObservacoes(opcional(request.observacoes()));
        nova = assinaturaRepository.saveAndFlush(nova);
        gerarCobrancas(nova, hoje().plusMonths(MESES_ANTECEDENCIA));
        backofficeLogService.registrar("ASSINATURA_CRIAR", "ASSINATURA", nova.getId(), tenantId);
        return montarFinanceiro(tenantId);
    }

    @Transactional
    public FinanceiroResponse cancelarAssinatura(UUID tenantId) {
        buscarTenant(tenantId);
        Assinatura atual = assinaturaRepository.findByTenantIdAndStatus(tenantId, Assinatura.Status.ATIVA)
                .orElseThrow(() -> new ResourceNotFoundException("Paróquia sem assinatura ativa."));
        LocalDate hoje = hoje();
        atual.encerrar(hoje);
        cancelarAbertasAPartirDe(atual, hoje.plusDays(1), "Assinatura encerrada");
        backofficeLogService.registrar("ASSINATURA_ENCERRAR", "ASSINATURA", atual.getId(), tenantId);
        return montarFinanceiro(tenantId);
    }

    @Transactional
    public FinanceiroResponse gerarAdiantadas(UUID tenantId, YearMonth ate) {
        buscarTenant(tenantId);
        Assinatura atual = assinaturaRepository.findByTenantIdAndStatus(tenantId, Assinatura.Status.ATIVA)
                .orElseThrow(() -> new ResourceNotFoundException("Paróquia sem assinatura ativa."));
        YearMonth limite = YearMonth.from(hoje()).plusMonths(MAX_MESES_ADIANTADOS);
        if (ate.isAfter(limite)) {
            throw new BadRequestException("Gere no máximo " + MAX_MESES_ADIANTADOS + " meses à frente.");
        }
        int geradas = gerarCobrancas(atual, ate.atEndOfMonth());
        if (geradas > 0) {
            backofficeLogService.registrar("COBRANCA_GERAR", "ASSINATURA", atual.getId(), tenantId);
        }
        return montarFinanceiro(tenantId);
    }

    /**
     * Registra o pagamento de uma ou mais cobranças. Efeitos na paróquia:
     * {@code ultimo_pagamento_em = agora}, {@code vigencia_ate} estendida
     * até o fim da competência paga mais distante e, se não sobrou nada
     * vencido, TRIAL/BLOQUEADO vira ATIVO (o que o antigo "marcar como
     * pago" fazia, agora com critério).
     */
    @Transactional
    public FinanceiroResponse registrarPagamento(UUID tenantId, RegistrarPagamentoRequest request) {
        Tenant tenant = buscarTenant(tenantId);
        if (tenant.getStatus() == Tenant.Status.CANCELADO) {
            throw new BadRequestException("Paróquia cancelada não recebe pagamento.");
        }
        LocalDate hoje = hoje();
        if (request.pagoEm().isAfter(hoje)) {
            throw new BadRequestException("A data do pagamento não pode estar no futuro.");
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(request.cobrancaIds()));
        if (request.valorPago() != null && ids.size() > 1) {
            throw new BadRequestException("Valor pago só pode ser informado para uma cobrança por vez.");
        }
        List<Cobranca> cobrancas = buscarCobrancas(tenantId, ids);
        for (Cobranca cobranca : cobrancas) {
            if (cobranca.getStatus() != Cobranca.Status.ABERTA) {
                throw new BadRequestException("A cobrança de " + competencia(cobranca) + " não está em aberto.");
            }
        }

        UUID operadorId = operadorAtualId();
        String observacao = opcional(request.observacao());
        LocalDate fimMaisDistante = null;
        for (Cobranca cobranca : cobrancas) {
            BigDecimal valorPago = request.valorPago() != null ? request.valorPago() : cobranca.getValor();
            cobranca.pagar(request.pagoEm(), valorPago, request.formaPagamento(), observacao, operadorId);
            if (fimMaisDistante == null || cobranca.getCompetenciaFim().isAfter(fimMaisDistante)) {
                fimMaisDistante = cobranca.getCompetenciaFim();
            }
            backofficeLogService.registrar("PAGAMENTO_REGISTRAR", "COBRANCA", cobranca.getId(), tenantId);
        }
        entityManager.flush();

        tenant.setUltimoPagamentoEm(clock.instant());
        if (tenant.getVigenciaAte() == null || fimMaisDistante.isAfter(tenant.getVigenciaAte())) {
            tenant.setVigenciaAte(fimMaisDistante);
        }
        boolean aindaEmAtraso = cobrancaRepository.existsByTenantIdAndStatusAndVencimentoBefore(
                tenantId, Cobranca.Status.ABERTA, hoje);
        if (!aindaEmAtraso
                && (tenant.getStatus() == Tenant.Status.TRIAL || tenant.getStatus() == Tenant.Status.BLOQUEADO)) {
            tenant.setStatus(Tenant.Status.ATIVO);
        }
        return montarFinanceiro(tenantId);
    }

    /**
     * Desfaz um pagamento lançado por engano. Não mexe no status nem na
     * vigência da paróquia — se for o caso, o operador bloqueia/ajusta à mão
     * (mesma regra: bloqueio nunca é automático).
     */
    @Transactional
    public FinanceiroResponse estornar(UUID tenantId, UUID cobrancaId) {
        buscarTenant(tenantId);
        Cobranca cobranca = buscarCobrancas(tenantId, List.of(cobrancaId)).getFirst();
        if (cobranca.getStatus() != Cobranca.Status.PAGA) {
            throw new BadRequestException("Só é possível estornar cobrança paga.");
        }
        cobranca.estornar();
        backofficeLogService.registrar("PAGAMENTO_ESTORNAR", "COBRANCA", cobranca.getId(), tenantId);
        return montarFinanceiro(tenantId);
    }

    @Transactional
    public FinanceiroResponse isentar(UUID tenantId, UUID cobrancaId, String motivo) {
        buscarTenant(tenantId);
        Cobranca cobranca = buscarCobrancas(tenantId, List.of(cobrancaId)).getFirst();
        if (cobranca.getStatus() != Cobranca.Status.ABERTA) {
            throw new BadRequestException("Só é possível isentar cobrança em aberto.");
        }
        String texto = opcional(motivo);
        cobranca.cancelar(texto == null ? "Isenta" : texto);
        backofficeLogService.registrar("COBRANCA_ISENTAR", "COBRANCA", cobranca.getId(), tenantId);
        return montarFinanceiro(tenantId);
    }

    /** Usado pelo {@link BillingJob}: gera as cobranças de todas as assinaturas ativas. */
    @Transactional
    public int gerarCobrancasDeTodas() {
        LocalDate ate = hoje().plusMonths(MESES_ANTECEDENCIA);
        int total = 0;
        for (Assinatura assinatura : assinaturaRepository.findByStatus(Assinatura.Status.ATIVA)) {
            total += gerarCobrancas(assinatura, ate);
        }
        return total;
    }

    /**
     * Plano e atraso de cada paróquia para a listagem do backoffice — duas
     * queries para a lista inteira (sem N+1). Paróquia sem assinatura nem
     * atraso fica fora do mapa (use {@link SituacaoFinanceira#VAZIA}).
     */
    @Transactional(readOnly = true)
    public Map<UUID, SituacaoFinanceira> situacaoPorTenant(Collection<UUID> tenantIds) {
        Map<UUID, SituacaoFinanceira> resultado = new HashMap<>();
        if (tenantIds.isEmpty()) {
            return resultado;
        }
        LocalDate hoje = hoje();
        Map<UUID, Assinatura> ativas = new HashMap<>();
        for (Assinatura a : assinaturaRepository.findByTenantIdInAndStatus(
                List.copyOf(tenantIds), Assinatura.Status.ATIVA)) {
            ativas.put(a.getTenantId(), a);
        }
        Map<UUID, Object[]> atrasos = new HashMap<>();
        for (Object[] linha : cobrancaRepository.resumoAtrasoPorTenant(Cobranca.Status.ABERTA, hoje)) {
            atrasos.put((UUID) linha[0], linha);
        }
        for (UUID tenantId : tenantIds) {
            Assinatura assinatura = ativas.get(tenantId);
            Object[] atraso = atrasos.get(tenantId);
            if (assinatura == null && atraso == null) {
                continue;
            }
            long vencidas = atraso == null ? 0 : ((Number) atraso[1]).longValue();
            long dias = atraso == null ? 0 : ChronoUnit.DAYS.between((LocalDate) atraso[2], hoje);
            resultado.put(tenantId, new SituacaoFinanceira(
                    assinatura == null ? null : assinatura.getPlano().getNome(),
                    assinatura == null ? null : assinatura.getPeriodicidade(),
                    vencidas, dias));
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public long contarParoquiasEmAtraso() {
        return cobrancaRepository.contarTenantsEmAtraso(Cobranca.Status.ABERTA, hoje());
    }

    @Transactional(readOnly = true)
    public BigDecimal recebidoNoMes() {
        YearMonth mes = YearMonth.from(hoje());
        return cobrancaRepository.somarRecebido(Cobranca.Status.PAGA, mes.atDay(1), mes.atEndOfMonth());
    }

    /**
     * Gera as cobranças que faltam com {@code competencia_inicio <= ate}
     * (e dentro do contrato, se ele já tiver fim). Devolve quantas criou.
     */
    int gerarCobrancas(Assinatura assinatura, LocalDate ate) {
        Set<LocalDate> existentes = new HashSet<>();
        for (Cobranca c : cobrancaRepository.findByAssinaturaId(assinatura.getId())) {
            existentes.add(c.getCompetenciaInicio());
        }
        LocalDate limite = assinatura.getFim() != null && assinatura.getFim().isBefore(ate)
                ? assinatura.getFim()
                : ate;
        int meses = assinatura.getPeriodicidade().meses();
        LocalDate primeiro = assinatura.getPeriodicidade() == Periodicidade.MENSAL
                ? assinatura.getInicio().withDayOfMonth(1)
                : assinatura.getInicio();

        List<Cobranca> novas = new ArrayList<>();
        for (int i = 0; ; i++) {
            LocalDate inicioPeriodo = primeiro.plusMonths((long) i * meses);
            if (inicioPeriodo.isAfter(limite)) {
                break;
            }
            if (existentes.contains(inicioPeriodo)) {
                continue;
            }
            LocalDate fimPeriodo = primeiro.plusMonths((long) (i + 1) * meses).minusDays(1);
            LocalDate vencimento = inicioPeriodo.withDayOfMonth(assinatura.getDiaVencimento());
            if (vencimento.isBefore(assinatura.getInicio())) {
                vencimento = assinatura.getInicio();
            }
            novas.add(new Cobranca(assinatura, inicioPeriodo, fimPeriodo, vencimento));
        }
        cobrancaRepository.saveAll(novas);
        return novas.size();
    }

    private void cancelarAbertasAPartirDe(Assinatura assinatura, LocalDate data, String motivo) {
        for (Cobranca c : cobrancaRepository.findByAssinaturaId(assinatura.getId())) {
            if (c.getStatus() == Cobranca.Status.ABERTA && !c.getCompetenciaInicio().isBefore(data)) {
                c.cancelar(motivo);
            }
        }
    }

    private FinanceiroResponse montarFinanceiro(UUID tenantId) {
        LocalDate hoje = hoje();
        List<Assinatura> assinaturas = assinaturaRepository.findByTenantIdOrderByInicioDescCreatedAtDesc(tenantId);
        AssinaturaResponse atual = assinaturas.stream()
                .filter(a -> a.getStatus() == Assinatura.Status.ATIVA)
                .findFirst().map(AssinaturaResponse::de).orElse(null);
        List<Cobranca> cobrancas = cobrancaRepository.findByTenantIdOrderByCompetenciaInicioDesc(tenantId);

        List<Cobranca> vencidas = cobrancas.stream().filter(c -> c.vencidaEm(hoje)).toList();
        long diasAtraso = vencidas.stream().map(Cobranca::getVencimento).min(Comparator.naturalOrder())
                .map(v -> ChronoUnit.DAYS.between(v, hoje)).orElse(0L);
        BigDecimal valorEmAtraso = vencidas.stream().map(Cobranca::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate proximoVencimento = cobrancas.stream()
                .filter(c -> c.getStatus() == Cobranca.Status.ABERTA && !c.getVencimento().isBefore(hoje))
                .map(Cobranca::getVencimento).min(Comparator.naturalOrder()).orElse(null);
        BigDecimal totalPagoNoAno = cobrancas.stream()
                .filter(c -> c.getStatus() == Cobranca.Status.PAGA && c.getPagoEm().getYear() == hoje.getYear())
                .map(Cobranca::getValorPago).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinanceiroResponse(
                atual,
                assinaturas.stream().map(AssinaturaResponse::de).toList(),
                cobrancas.stream().map(c -> CobrancaResponse.de(c, hoje)).toList(),
                new FinanceiroResponse.Resumo(vencidas.size(), diasAtraso, valorEmAtraso,
                        proximoVencimento, totalPagoNoAno));
    }

    private List<Cobranca> buscarCobrancas(UUID tenantId, List<UUID> ids) {
        List<Cobranca> cobrancas = cobrancaRepository.findByIdInAndTenantId(ids, tenantId);
        if (cobrancas.size() != ids.size()) {
            throw new ResourceNotFoundException("Cobrança não encontrada.");
        }
        return cobrancas;
    }

    private Tenant buscarTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Paróquia não encontrada."));
    }

    private static String competencia(Cobranca cobranca) {
        return cobranca.getCompetenciaInicio().format(MES_ANO);
    }

    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static UUID operadorAtualId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser usuario) {
            return usuario.usuarioId();
        }
        return null;
    }
}
