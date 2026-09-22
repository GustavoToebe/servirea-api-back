package br.com.servire.api.inscricao.dto;

import jakarta.validation.constraints.NotBlank;

/** Payload de {@code POST /inscricoes/{id}/rejeitar} — motivo é obrigatório (CHECK {@code inscricoes_rejeitada_ck}, V008). */
public record InscricaoRejeitarRequest(@NotBlank String motivo) {
}
