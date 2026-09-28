package br.com.servire.api.comunicacao.dto;

/** O token nunca volta: só se está configurado. */
public record WhatsappConfigResponse(String instancia, boolean ativo, boolean tokenConfigurado) {
}
