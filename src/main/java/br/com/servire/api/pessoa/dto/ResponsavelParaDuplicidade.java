package br.com.servire.api.pessoa.dto;

import java.util.UUID;

public record ResponsavelParaDuplicidade(
        UUID voluntarioId,
        String nomeCompleto
) {
}
