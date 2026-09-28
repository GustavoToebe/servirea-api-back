package br.com.servire.api.escala.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record EscalaEventoRequest(
        @NotNull LocalDate data,
        @NotNull LocalTime horario,
        String celebracao,
        @NotEmpty List<@Valid EscalaVagaRequest> vagas,
        Boolean referencia) {

    public EscalaEventoRequest(LocalDate data, LocalTime horario, String celebracao, List<EscalaVagaRequest> vagas) {
        this(data, horario, celebracao, vagas, null);
    }
}
