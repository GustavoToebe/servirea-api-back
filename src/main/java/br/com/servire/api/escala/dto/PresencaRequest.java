package br.com.servire.api.escala.dto;

import br.com.servire.api.escala.Presenca;
import jakarta.validation.constraints.NotNull;

public record PresencaRequest(@NotNull Presenca presenca) {
}
