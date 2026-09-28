package br.com.servire.api.pessoa.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DuplicidadeRequest(
        UUID ignorarId,
        String nomeCompleto,
        String cpf,
        LocalDate dataNascimento,
        List<String> telefones,
        List<String> nomesResponsaveis
) {
}
