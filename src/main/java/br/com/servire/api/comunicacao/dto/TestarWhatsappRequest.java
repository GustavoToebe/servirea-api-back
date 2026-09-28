package br.com.servire.api.comunicacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestarWhatsappRequest(@NotBlank @Size(max = 30) String telefone) {
}
