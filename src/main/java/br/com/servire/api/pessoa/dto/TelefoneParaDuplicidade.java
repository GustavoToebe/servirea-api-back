package br.com.servire.api.pessoa.dto;

import java.util.UUID;

public record TelefoneParaDuplicidade(
        UUID pessoaId,
        String numero
) {
}
