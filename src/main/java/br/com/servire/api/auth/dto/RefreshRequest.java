package br.com.servire.api.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * O refresh token em si vem do cookie HttpOnly (seção 92), nunca no corpo.
 * O corpo só precisa dizer PARA QUAL tenant o novo access token deve ser
 * emitido — decisão de design da Fase 5: {@code refresh_token} é global ao
 * usuário (seção 35, não amarrado a um tenant específico), então o
 * "tenant atual" precisa vir de algum outro lugar a cada refresh. Exigir
 * isso explícito no corpo evita ter que decodificar um access token já
 * expirado (frágil) ou alterar o schema de {@code refresh_token} para
 * carregar tenant (contrariaria a decisão da seção 35).
 */
public record RefreshRequest(@NotNull UUID tenantId) {
}
