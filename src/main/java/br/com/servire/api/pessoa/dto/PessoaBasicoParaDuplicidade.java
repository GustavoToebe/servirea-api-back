package br.com.servire.api.pessoa.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PessoaBasicoParaDuplicidade(
        UUID id,
        Long sequencial,
        String nomeCompleto,
        String cpf,
        LocalDate dataNascimento
) {
}
