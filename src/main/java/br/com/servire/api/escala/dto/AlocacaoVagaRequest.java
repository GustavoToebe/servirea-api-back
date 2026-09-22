package br.com.servire.api.escala.dto;

import java.util.UUID;

/**
 * Corpo do {@code PATCH /escalas/vagas/{vagaId}}. {@code voluntarioId}
 * nulo esvazia a vaga (desaloca).
 */
public record AlocacaoVagaRequest(UUID voluntarioId) {
}
