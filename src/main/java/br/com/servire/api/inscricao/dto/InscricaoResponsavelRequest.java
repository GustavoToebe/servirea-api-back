package br.com.servire.api.inscricao.dto;

import jakarta.validation.constraints.NotBlank;

/** Um responsável dentro do payload de inscrição (seção 108, mesma regra de {@code ResponsavelRequest} da Fase 6). */
public record InscricaoResponsavelRequest(
        @NotBlank String parentesco,
        @NotBlank String nome,
        String telefone,
        String celular,
        String email,
        boolean principal) {
}
