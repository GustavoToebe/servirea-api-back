package br.com.servire.api.comunicacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Token vazio mantém o atual. */
public record WhatsappConfigRequest(@NotBlank @Size(max = 120) String instancia, @Size(max = 300) String token, boolean ativo) {
}
