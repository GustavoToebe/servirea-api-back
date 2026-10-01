package br.com.servire.api.comunicacao;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * WhatsApp dos comunicados (PLANO-005), prefixo {@code servire.whatsapp}.
 * {@code provider}: {@code log} (padrão, nunca envia) ou {@code evolution}.
 * Nenhum segredo aqui: o token é da instância de cada paróquia
 * ({@code paroquia_whatsapp}). Os intervalos entre mensagens reduzem o
 * risco de o número ser bloqueado.
 */
@ConfigurationProperties(prefix = "servire.whatsapp")
public record WhatsappProperties(
        @DefaultValue("log") String provider,
        @DefaultValue Evolution evolution,
        @DefaultValue("15000") long intervaloMinMs,
        @DefaultValue("40000") long intervaloMaxMs) {

    public record Evolution(String url, @DefaultValue("/send/text") String caminhoTexto) {
    }
}
