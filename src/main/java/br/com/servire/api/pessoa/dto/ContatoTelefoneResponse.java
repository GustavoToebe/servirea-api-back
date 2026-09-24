package br.com.servire.api.pessoa.dto;

import java.util.UUID;

public record ContatoTelefoneResponse(UUID id, String tipo, String numero, boolean principal) {
}
