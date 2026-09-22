package br.com.servire.api.inscricao;

import br.com.servire.api.web.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Validação do Cloudflare Turnstile (Fase 8, seção 44 do plano mestre) —
 * chamada {@code POST https://challenges.cloudflare.com/turnstile/v0/siteverify}
 * (endpoint configurável via {@code servire.turnstile.verify-url}, mas o
 * padrão já é o oficial da Cloudflare).
 *
 * <p><b>Regra inegociável: falha FECHADO.</b> Se {@code secretKey} não
 * estiver configurada, se o token do cliente vier vazio, se a chamada HTTP
 * falhar (Cloudflare fora do ar, timeout, erro de rede) ou se a resposta
 * disser {@code success: false}, a inscrição é RECUSADA. Nunca existe um
 * caminho onde uma falha de infraestrutura própria (Turnstile configurado
 * errado, Cloudflare fora do ar) resulta em deixar passar sem verificar —
 * isso inverteria o próprio propósito do anti-robô.</p>
 *
 * <p>Usa {@code Map<String,Object>} para ler a resposta em vez de um
 * record dedicado — a resposta da Cloudflare tem campos que não são
 * identificadores Java válidos (ex.: {@code error-codes}) e não
 * precisamos de mais que o campo {@code success}; evita depender de
 * anotação de (de)serialização específica de uma versão do Jackson.</p>
 */
@Service
public class TurnstileService {

    private static final Logger log = LoggerFactory.getLogger(TurnstileService.class);

    private final RestClient restClient;
    private final TurnstileProperties properties;

    public TurnstileService(RestClient.Builder restClientBuilder, TurnstileProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public void validar(String token, String ipRemetente) {
        if (properties.secretKey() == null || properties.secretKey().isBlank()) {
            // Configuração ausente é um bug de operação/deploy, não do
            // cliente — 500 genérico (GlobalExceptionHandler), nunca
            // silenciosamente tratado como "verificação passou".
            throw new IllegalStateException(
                    "Turnstile não configurado (servire.turnstile.secret-key ausente) — recusando por segurança (fail-closed).");
        }
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Verificação anti-robô (Turnstile) obrigatória.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.secretKey());
        form.add("response", token);
        if (ipRemetente != null && !ipRemetente.isBlank()) {
            form.add("remoteip", ipRemetente);
        }

        Map<String, Object> resposta;
        try {
            resposta = restClient.post()
                    .uri(properties.verifyUrl())
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.warn("Falha ao chamar o Turnstile (fail-closed: inscrição recusada)", e);
            throw new BadRequestException("Não foi possível validar a verificação anti-robô agora. Tente novamente.");
        }

        Object success = resposta == null ? null : resposta.get("success");
        if (!Boolean.TRUE.equals(success)) {
            throw new BadRequestException("Verificação anti-robô (Turnstile) falhou.");
        }
    }
}
