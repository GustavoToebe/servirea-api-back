package br.com.servire.api.integracao;

import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Erros de servidor (5xx) mandados à Central para a tela "Logs" (contrato
 * 6.2, decisão de 26/09/2026). Só vai o que ajuda a achar o problema sem
 * dado pessoal: usuário pelo id, mensagem só quando é nossa (erro previsto);
 * no inesperado vai o tipo da exceção, porque a mensagem técnica pode trazer
 * valores do banco.
 *
 * <p>Fila em memória, enviada a cada minuto: a requisição da paróquia nunca
 * espera nem falha por causa da Central. Guarda no máximo {@value #MAXIMO}
 * (os mais antigos saem primeiro) e perde o que não foi enviado se a API
 * reiniciar — aceitável para um registro de apoio.
 */
@Component
public class RelatorioDeErros {

    static final int MAXIMO = 500;
    static final int LOTE = 100;
    static final String CAMINHO = "/integracao/v1/produtos/SERVIRE/erros";
    static final int TAMANHO_MENSAGEM = 1000;

    private static final Logger log = LoggerFactory.getLogger(RelatorioDeErros.class);

    public record Erro(UUID id, Instant ocorridoEm, UUID tenantId, UUID usuarioId, String metodo, String rota,
                       int status, String codigo, String mensagem, String requestId) {
    }

    private final ConcurrentLinkedDeque<Erro> fila = new ConcurrentLinkedDeque<>();
    private final IntegracaoProperties properties;
    private final RestClient http;
    private final JsonMapper json;
    private final Clock clock;
    private boolean avisouFalha;

    @Autowired
    public RelatorioDeErros(IntegracaoProperties properties, RestClient.Builder http, JsonMapper json) {
        this(properties, http.build(), json, Clock.systemUTC());
    }

    RelatorioDeErros(IntegracaoProperties properties, RestClient http, JsonMapper json, Clock clock) {
        this.properties = properties;
        this.http = http;
        this.json = json;
        this.clock = clock;
    }

    /** Chamado pelo {@code GlobalExceptionHandler}. Erro 4xx (engano de quem digitou) não entra. */
    public void registrar(int status, String codigo, String mensagem, HttpServletRequest request) {
        if (status < 500) {
            return;
        }
        UUID tenantId = TenantContext.get();
        UUID usuarioId = null;
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof AuthenticatedUser usuario) {
            usuarioId = usuario.usuarioId();
            if (tenantId == null) {
                tenantId = usuario.tenantId();
            }
        }
        fila.addLast(new Erro(UUID.randomUUID(), clock.instant(), tenantId, usuarioId,
                request == null ? null : request.getMethod(),
                request == null ? null : request.getRequestURI(),
                status, codigo, cortar(mensagem), MDC.get(RequestIdFilter.MDC_KEY)));
        while (fila.size() > MAXIMO) {
            fila.pollFirst();
        }
    }

    public List<Erro> pendentes() {
        return List.copyOf(fila);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void enviar() {
        if (fila.isEmpty() || !configurado()) {
            return;
        }
        List<Erro> lote = new ArrayList<>();
        for (Erro erro : fila) {
            if (lote.size() == LOTE) {
                break;
            }
            lote.add(erro);
        }
        try {
            byte[] corpo = json.writeValueAsBytes(Map.of("erros", lote));
            String timestamp = Long.toString(clock.instant().getEpochSecond());
            String nonce = UUID.randomUUID().toString();
            byte[] segredo = Base64.getDecoder().decode(properties.chaveSaidaSegredo());
            String assinatura = HmacAssinatura.assinar(segredo, "POST", CAMINHO, timestamp, nonce, corpo);
            String base = properties.centralUrl().replaceAll("/+$", "");
            http.post()
                    .uri(base + CAMINHO)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(IntegracaoFiltro.CHAVE, properties.chaveSaidaId())
                    .header(IntegracaoFiltro.TIMESTAMP, timestamp)
                    .header(IntegracaoFiltro.NONCE, nonce)
                    .header(IntegracaoFiltro.ASSINATURA, assinatura)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity();
            fila.removeAll(lote);
            avisouFalha = false;
        } catch (RuntimeException e) {
            if (!avisouFalha) {
                log.warn("Não foi possível enviar {} erro(s) à Central; tento de novo em 1 minuto: {}",
                        lote.size(), e.getMessage());
                avisouFalha = true;
            }
        }
    }

    private boolean configurado() {
        return properties.centralUrl() != null && !properties.centralUrl().isBlank()
                && properties.chaveSaidaId() != null && !properties.chaveSaidaId().isBlank()
                && properties.chaveSaidaSegredo() != null && !properties.chaveSaidaSegredo().isBlank();
    }

    private static String cortar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= TAMANHO_MENSAGEM ? texto : texto.substring(0, TAMANHO_MENSAGEM);
    }
}
