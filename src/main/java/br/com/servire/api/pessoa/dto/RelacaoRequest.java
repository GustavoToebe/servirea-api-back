package br.com.servire.api.pessoa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Ligação com outra pessoa já cadastrada. {@code parentesco} é o "é"
 * (Pai, Mãe, Esposa); {@code parentescoInverso} é o rótulo do outro lado
 * (Filho, Esposo) — mesmo espírito do condominorelacionamento.
 */
public record RelacaoRequest(
        @NotNull UUID pessoaId,
        @NotBlank String parentesco,
        String parentescoInverso,
        boolean principal) {
}
