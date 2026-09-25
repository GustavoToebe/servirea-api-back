package br.com.servire.api.integracao;

import br.com.servire.api.auth.EmailSender;
import br.com.servire.api.integracao.dto.DireitosInstancia;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Puxa os direitos na Central a cada 8 horas e na subida. Se falhar, tenta
 * de novo a cada 30 minutos. Sem URL ou sem chave de saída, não faz nada
 * (a paróquia continua na cópia local).
 */
@Service
public class SincronizacaoDireitosService {

    private static final Logger log = LoggerFactory.getLogger(SincronizacaoDireitosService.class);

    private final IntegracaoProperties properties;
    private final IntegracaoInstanciaService instancias;
    private final DireitosLocaisRepository direitos;
    private final TenantRepository tenants;
    private final EmailSender emailSender;
    private final JsonMapper json;
    private final RestClient http;
    private volatile boolean pendente = true;
    private volatile Instant ultimoAlerta;

    public SincronizacaoDireitosService(IntegracaoProperties properties,
                                         IntegracaoInstanciaService instancias,
                                         DireitosLocaisRepository direitos,
                                         TenantRepository tenants,
                                         EmailSender emailSender,
                                         JsonMapper json,
                                         RestClient.Builder http) {
        this.properties = properties;
        this.instancias = instancias;
        this.direitos = direitos;
        this.tenants = tenants;
        this.emailSender = emailSender;
        this.json = json;
        this.http = http.build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void naSubida() {
        pendente = true;
        executar();
    }

    @Scheduled(cron = "0 0 */8 * * *")
    public void aCadaOitoHoras() {
        pendente = true;
        executar();
    }

    @Scheduled(fixedDelay = 1_800_000, initialDelay = 1_800_000)
    public void retentativa() {
        if (pendente) {
            executar();
        }
    }

    void executar() {
        if (properties.centralUrl() == null || properties.centralUrl().isBlank()
                || properties.chaveSaidaId() == null || properties.chaveSaidaId().isBlank()
                || properties.chaveSaidaSegredo() == null || properties.chaveSaidaSegredo().isBlank()) {
            pendente = false;
            return;
        }
        try {
            byte[] segredo = Base64.getDecoder().decode(properties.chaveSaidaSegredo());
            Set<UUID> vistos = new HashSet<>();
            int pagina = 0;
            while (true) {
                String caminho = "/integracao/v1/produtos/SERVIRE/direitos?pagina=" + pagina + "&tamanho=100";
                String corpo = pedir(segredo, caminho);
                JsonNode raiz = json.readTree(corpo);
                JsonNode itens = raiz.get("itens");
                int quantidade = itens == null || !itens.isArray() ? 0 : itens.size();
                if (itens != null) {
                    for (JsonNode item : itens) {
                        DireitosInstancia snapshot = json.treeToValue(item, DireitosInstancia.class);
                        if (snapshot.tenantId() == null) {
                            continue;
                        }
                        vistos.add(snapshot.tenantId());
                        if (direitos.existsById(snapshot.tenantId())) {
                            instancias.aplicarDireitos(snapshot.tenantId(), snapshot);
                        } else {
                            log.warn("Central devolveu tenant {} que esta instância não tem.", snapshot.tenantId());
                        }
                    }
                }
                int total = raiz.path("total").asInt(quantidade);
                pagina++;
                if (pagina * 100 >= total || quantidade == 0) {
                    break;
                }
            }
            for (DireitosLocais local : direitos.findAll()) {
                if (!vistos.contains(local.getTenantId()) && tenants.existsById(local.getTenantId())) {
                    log.warn("Paróquia {} não veio na sincronização da Central.", local.getTenantId());
                }
            }
            pendente = false;
        } catch (RuntimeException e) {
            pendente = true;
            log.error("Sincronização de direitos falhou; nova tentativa em 30 minutos.", e);
        } finally {
            verificarAtrasos();
        }
    }

    /**
     * Roda depois de toda tentativa, com sucesso ou não. Até 25/09/2026 o
     * alerta ficava dentro do caminho de sucesso: com a Central fora do ar
     * a exceção vinha antes e o e-mail nunca saía, justo no caso para o
     * qual ele existe. No máximo um e-mail a cada 8h (as tentativas são a
     * cada 30 min).
     */
    void verificarAtrasos() {
        try {
            List<String> linhas = new ArrayList<>();
            for (DireitosLocais local : direitos.findAll()) {
                Duration atraso = Duration.between(local.getConfirmadoEm(), Instant.now());
                if (atraso.toHours() < 24) {
                    continue;
                }
                String nome = tenants.findById(local.getTenantId()).map(Tenant::getNome)
                        .orElse(local.getTenantId().toString());
                boolean bloqueada = atraso.toHours() >= properties.tolerancia();
                linhas.add(nome + ": " + atraso.toHours() + "h sem confirmação da Central"
                        + (bloqueada ? " — BLOQUEADA pela tolerância de " + properties.tolerancia() + "h" : ""));
            }
            if (linhas.isEmpty()) {
                ultimoAlerta = null;
                return;
            }
            log.error("Paróquias sem confirmação da Central: {}", linhas);
            Instant agora = Instant.now();
            if (properties.alertaEmail() == null || properties.alertaEmail().isBlank()
                    || (ultimoAlerta != null && ultimoAlerta.isAfter(agora.minus(Duration.ofHours(8))))) {
                return;
            }
            emailSender.enviarAlertaIntegracao(properties.alertaEmail(), String.join("
", linhas));
            ultimoAlerta = agora;
        } catch (RuntimeException e) {
            log.error("Falha ao verificar atraso de sincronização.", e);
        }
    }

    private String pedir(byte[] segredo, String caminho) {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String assinatura = HmacAssinatura.assinar(segredo, "GET", caminho, timestamp, nonce, new byte[0]);
        String base = properties.centralUrl().endsWith("/")
                ? properties.centralUrl().substring(0, properties.centralUrl().length() - 1)
                : properties.centralUrl();
        return http.get()
                .uri(base + caminho)
                .accept(MediaType.APPLICATION_JSON)
                .header(IntegracaoFiltro.CHAVE, properties.chaveSaidaId())
                .header(IntegracaoFiltro.TIMESTAMP, timestamp)
                .header(IntegracaoFiltro.NONCE, nonce)
                .header(IntegracaoFiltro.ASSINATURA, assinatura)
                .retrieve()
                .body(String.class);
    }
}
