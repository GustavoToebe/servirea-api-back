package br.com.servire.api.comunicacao;

import br.com.servire.api.auth.Anexo;
import br.com.servire.api.auth.EmailSender;
import br.com.servire.api.integracao.AcessoParoquia;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Processa a fila dos comunicados a cada 15 s (PLANO-005). Por paróquia liberada: {@code TenantContext.set}
 * e só depois as transações ({@code TransactionTemplate}), a mesma armadilha do {@code InscricaoService.criarPublica}.
 * O envio em si roda fora de transação; cada resultado é gravado numa transação curta. Três falhas = FALHA.
 * Uma paróquia com erro não para as outras.
 */
@Component
public class FilaDeEnvio {

    private static final Logger log = LoggerFactory.getLogger(FilaDeEnvio.class);
    static final int LOTE = 30;
    private static final List<StatusComunicado> ABERTOS = List.of(StatusComunicado.NA_FILA, StatusComunicado.ENVIANDO);

    private final TenantRepository tenants;
    private final AcessoParoquia acessoParoquia;
    private final ComunicadoRepository comunicados;
    private final ComunicadoDestinatarioRepository destinatarios;
    private final ComunicadoAnexoRepository anexos;
    private final ParoquiaWhatsappService whatsappConfig;
    private final EmailSender emailSender;
    private final WhatsappSender whatsappSender;
    private final WhatsappProperties properties;
    private final TransactionTemplate tx;
    private final boolean ativa;
    private final long pausaEmailMs;

    public FilaDeEnvio(TenantRepository tenants, AcessoParoquia acessoParoquia, ComunicadoRepository comunicados,
                       ComunicadoDestinatarioRepository destinatarios, ComunicadoAnexoRepository anexos,
                       ParoquiaWhatsappService whatsappConfig, EmailSender emailSender, WhatsappSender whatsappSender,
                       WhatsappProperties properties, PlatformTransactionManager transactionManager,
                       @Value("${servire.comunicado.fila-ativa:true}") boolean ativa,
                       @Value("${servire.comunicado.pausa-email-ms:600}") long pausaEmailMs) {
        this.tenants = tenants;
        this.acessoParoquia = acessoParoquia;
        this.comunicados = comunicados;
        this.destinatarios = destinatarios;
        this.anexos = anexos;
        this.whatsappConfig = whatsappConfig;
        this.emailSender = emailSender;
        this.whatsappSender = whatsappSender;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.ativa = ativa;
        this.pausaEmailMs = pausaEmailMs;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    void agendado() {
        if (ativa) processarAgora();
    }

    /** Uma rodada da fila em todas as paróquias liberadas. Público para os testes. */
    public void processarAgora() {
        for (Tenant tenant : tenants.findAll()) {
            try {
                if (!acessoParoquia.liberada(tenant)) continue;
                TenantContext.set(tenant.getId());
                try {
                    processarParoquia(tenant);
                } finally {
                    TenantContext.clear();
                }
            } catch (RuntimeException e) {
                log.error("Fila de comunicados falhou na paróquia {}", tenant.getId(), e);
            }
        }
    }

    /** O que o envio precisa, lido antes de sair da transação. */
    private record Item(UUID id, UUID comunicadoId, TipoEnvio canal, String destino, String assunto, String conteudo) {
    }

    private void processarParoquia(Tenant tenant) {
        List<Item> lote = tx.execute(s -> {
            List<ComunicadoDestinatario> ds = destinatarios.proximosDaFila(ABERTOS, PageRequest.of(0, LOTE));
            Map<UUID, Comunicado> porId = new HashMap<>();
            for (ComunicadoDestinatario d : ds) {
                porId.computeIfAbsent(d.getComunicadoId(), id -> comunicados.findById(id).orElseThrow()).marcarEnviando();
            }
            return ds.stream().map(d -> new Item(d.getId(), d.getComunicadoId(), porId.get(d.getComunicadoId()).getCanal(),
                    d.getDestino(), d.getAssunto(), d.getConteudo())).toList();
        });
        if (lote == null || lote.isEmpty()) return;

        Set<UUID> tocados = new LinkedHashSet<>();
        Map<UUID, List<Anexo>> anexosPorComunicado = new HashMap<>();
        String responderPara = tx.execute(s -> tenants.findById(tenant.getId()).flatMap(ComunicadoService::emailPrincipal).orElse(null));
        Optional<ParoquiaWhatsapp> zap = lote.stream().anyMatch(i -> i.canal() == TipoEnvio.WHATSAPP)
                ? whatsappConfig.ativa() : Optional.empty();

        boolean primeiro = true;
        for (Item item : lote) {
            if (!primeiro) pausar(item.canal());
            primeiro = false;
            tocados.add(item.comunicadoId());
            String erro = null;
            try {
                if (item.canal() == TipoEnvio.EMAIL) {
                    List<Anexo> lista = anexosPorComunicado.computeIfAbsent(item.comunicadoId(), this::anexosDe);
                    emailSender.enviarComunicado(item.destino(), item.assunto(), item.conteudo(), lista, responderPara);
                } else {
                    ParoquiaWhatsapp c = zap.orElseThrow(() -> new IllegalStateException("WhatsApp da paróquia desativado."));
                    whatsappSender.enviarTexto(c.getInstancia(), c.getToken(), item.destino(), item.conteudo());
                }
            } catch (RuntimeException e) {
                erro = e instanceof IllegalStateException ? e.getMessage() : "Falha no envio (" + e.getClass().getSimpleName() + ").";
                log.warn("Envio de comunicado falhou na paróquia {}: {}", tenant.getId(), e.getClass().getSimpleName());
            }
            String erroFinal = erro;
            tx.executeWithoutResult(s -> destinatarios.findById(item.id()).ifPresent(d -> {
                if (erroFinal == null) d.marcarEnviado();
                else d.registrarFalha(erroFinal);
            }));
        }
        tx.executeWithoutResult(s -> tocados.forEach(this::atualizarContagem));
    }

    private List<Anexo> anexosDe(UUID comunicadoId) {
        return tx.execute(s -> anexos.findByComunicadoIdOrderByNome(comunicadoId).stream()
                .filter(a -> a.getConteudo() != null)
                .map(a -> new Anexo(a.getNome(), a.getTipo(), a.getConteudo())).toList());
    }

    private void atualizarContagem(UUID comunicadoId) {
        comunicados.findById(comunicadoId).ifPresent(c -> {
            c.contar((int) destinatarios.countByComunicadoIdAndStatus(comunicadoId, StatusEnvio.ENVIADO),
                    (int) destinatarios.countByComunicadoIdAndStatus(comunicadoId, StatusEnvio.FALHA));
            if (destinatarios.countByComunicadoIdAndStatus(comunicadoId, StatusEnvio.PENDENTE) == 0) {
                c.concluir();
                anexos.findByComunicadoIdOrderByNome(comunicadoId).forEach(ComunicadoAnexo::apagarConteudo);
            }
        });
    }

    private void pausar(TipoEnvio canal) {
        // E-mail: respeita o limite de envios por segundo do Resend.
        long ms = canal == TipoEnvio.EMAIL ? pausaEmailMs : aleatorio(properties.intervaloMinMs(), properties.intervaloMaxMs());
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static long aleatorio(long min, long max) {
        return max <= min ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
    }
}
