package br.com.servire.api.comunicacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * WhatsApp pelo Evolution Go (API não oficial, decisão do usuário em 28/09/2026).
 *
 * <p><b>A conferir na instância real:</b> caminho ({@code servire.whatsapp.evolution.caminho-texto},
 * padrão {@code /send/text}), header {@code apikey: <token da instância>} e corpo
 * {@code {"number": "5545999998888", "text": "..."}} seguem a documentação pública do Evolution Go e
 * ainda não foram testados contra um servidor real; por isso são configuráveis.</p>
 */
@Component
@ConditionalOnProperty(prefix = "servire.whatsapp", name = "provider", havingValue = "evolution")
public class EvolutionGoWhatsappSender implements WhatsappSender {

    private static final Logger log = LoggerFactory.getLogger(EvolutionGoWhatsappSender.class);

    private final RestClient restClient;
    private final WhatsappProperties properties;

    public EvolutionGoWhatsappSender(RestClient.Builder builder, WhatsappProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @Override
    public void enviarTexto(String instancia, String token, String telefone, String texto) {
        String url = properties.evolution().url();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("servire.whatsapp.provider=evolution sem EVOLUTION_URL.");
        }
        try {
            restClient.post()
                    .uri(url.replaceAll("/+$", "") + properties.evolution().caminhoTexto())
                    .header("apikey", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("number", numero(telefone), "text", texto))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Falha no provedor WhatsApp: {}", e.getClass().getSimpleName());
            throw new WhatsappException("Não foi possível enviar a mensagem pelo WhatsApp.", e);
        }
    }

    /** Só dígitos; com 10 ou 11 dígitos (DDD + número), prefixa o 55 do Brasil. */
    static String numero(String telefone) {
        String digitos = telefone == null ? "" : telefone.replaceAll("\\D", "");
        return digitos.length() == 10 || digitos.length() == 11 ? "55" + digitos : digitos;
    }
}
