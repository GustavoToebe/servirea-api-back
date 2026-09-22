package br.com.servire.api.escala.dto;

import br.com.servire.api.voluntario.FuncaoEscala;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record EscalaVagaRequest(@NotNull FuncaoEscala funcao, @Positive int posicao, UUID voluntarioId) {
}
