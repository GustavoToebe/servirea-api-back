package br.com.servire.api.voluntario.dto;

/**
 * Ativos e inativos da paróquia numa resposta só, para a tela de pessoas
 * não fazer duas chamadas de contagem.
 */
public record ContagemVoluntarios(long ativos, long inativos) {
}
