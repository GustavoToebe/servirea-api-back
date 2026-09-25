package br.com.servire.api.integracao;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "servire.integracao")
public record IntegracaoProperties(
        String chavesEntrada,
        String chaveSaidaId,
        String chaveSaidaSegredo,
        String centralUrl,
        Integer toleranciaHoras,
        String alertaEmail
) {
    public int tolerancia() {
        return toleranciaHoras == null || toleranciaHoras < 1 ? 72 : toleranciaHoras;
    }

    /** {@code id:segredoBase64} separado por vírgula. Vazio = integração recusa tudo. */
    public Map<String, byte[]> chaves() {
        Map<String, byte[]> mapa = new LinkedHashMap<>();
        if (chavesEntrada == null || chavesEntrada.isBlank()) {
            return mapa;
        }
        for (String item : chavesEntrada.split(",")) {
            String[] partes = item.trim().split(":", 2);
            if (partes.length == 2 && !partes[0].isBlank() && !partes[1].isBlank()) {
                mapa.put(partes[0].trim(), Base64.getDecoder().decode(partes[1].trim()));
            }
        }
        return mapa;
    }
}
