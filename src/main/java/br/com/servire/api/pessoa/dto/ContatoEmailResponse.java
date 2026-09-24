package br.com.servire.api.pessoa.dto;

import java.util.UUID;

public record ContatoEmailResponse(UUID id, String tipo, String email, boolean principal) {
}
