package br.com.servire.api.minhaconta;

import br.com.servire.api.integracao.HmacAssinatura;
import br.com.servire.api.integracao.IntegracaoProperties;
import br.com.servire.api.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class MinhaContaService {

    private final IntegracaoProperties properties;
    private final RestClient.Builder httpBuilder;

    public MinhaContaService(IntegracaoProperties properties, RestClient.Builder httpBuilder) {
        this.properties = properties;
        this.httpBuilder = httpBuilder;
    }

    public String obterDadosMinhaConta() {
        if (properties.centralUrl() == null || properties.centralUrl().isBlank()
                || properties.chaveSaidaId() == null || properties.chaveSaidaId().isBlank()
                || properties.chaveSaidaSegredo() == null || properties.chaveSaidaSegredo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Integração com a Central não está configurada.");
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Instância não provisionada.");
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível consultar sua conta agora. Tente de novo em instantes.");
        }
    }
}
