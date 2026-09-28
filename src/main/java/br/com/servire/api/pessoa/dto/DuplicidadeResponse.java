package br.com.servire.api.pessoa.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DuplicidadeResponse(
        UUID id,
        Long sequencial,
        String nomeCompleto,
        LocalDate dataNascimento,
        List<String> motivos,
        boolean bloqueia
) {
}
