package br.com.servire.api.minhaconta;

import br.com.servirea.comum.seguranca.HmacAssinatura;

import br.com.servire.api.integracao.IntegracaoException;
import br.com.servire.api.integracao.IntegracaoProperties;
import br.com.servire.api.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * "Minha conta" da paróquia: os dados comerciais ficam na Central, o Servirea só repassa.
 * A instância é a própria paróquia (idExterno = tenant do JWT). Falha da Central vira 502 com
 * mensagem para a pessoa; integração sem chave vira 503.
 */
@Service
public class MinhaContaService {

    private static final Logger log = LoggerFactory.getLogger(MinhaContaService.class);
    static final String MENSAGEM_INDISPONIVEL = "Não foi possível consultar sua conta agora. Tente de novo em instantes.";

    private final IntegracaoProperties properties;
    private final RestClient.Builder httpBuilder;

    public MinhaContaService(IntegracaoProperties properties, RestClient.Builder httpBuilder) {
        this.properties = properties;
        this.httpBuilder = httpBuilder;
    }

    public String obterDadosMinhaConta() {
        if (vazio(properties.centralUrl()) || vazio(properties.chaveSaidaId()) || vazio(properties.chaveSaidaSegredo())) {
            throw new IntegracaoException(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRACAO_NAO_CONFIGURADA",
                    "A consulta da conta ainda não está disponível. Fale com o suporte.");
        }

        UUID tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant não encontrado no contexto");
        }

        byte[] segredo = Base64.getDecoder().decode(properties.chaveSaidaSegredo());
        String caminho = "/integracao/v1/produtos/SERVIREA/instancias/" + tenantId + "/minha-conta";
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String assinatura = HmacAssinatura.assinar(segredo, "GET", caminho, timestamp, nonce, new byte[0]);
        String base = properties.centralUrl().endsWith("/")
                ? properties.centralUrl().substring(0, properties.centralUrl().length() - 1)
                : properties.centralUrl();

        try {
            return httpBuilder.build().get()
                    .uri(base + caminho)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("X-Integracao-Chave", properties.chaveSaidaId())
                    .header("X-Integracao-Timestamp", timestamp)
                    .header("X-Integracao-Nonce", nonce)
                    .header("X-Integracao-Assinatura", assinatura)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IntegracaoException(HttpStatus.NOT_FOUND, "CONTA_NAO_ENCONTRADA",
                    "Sua paróquia ainda não tem uma contratação ativa na Central.");
        } catch (RestClientException e) {
            // 5xx, fora do ar ou assinatura recusada (401/403): para a pessoa é a mesma coisa.
            log.warn("Minha conta: a Central não respondeu como esperado ({})", e.getMessage());
            throw new IntegracaoException(HttpStatus.BAD_GATEWAY, "CENTRAL_INDISPONIVEL", MENSAGEM_INDISPONIVEL);
        }
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
