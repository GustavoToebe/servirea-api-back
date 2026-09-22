package br.com.servire.api.escala.dto;

import br.com.servire.api.escala.EscalaEvento;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record EscalaEventoResponse(UUID id, LocalDate data, LocalTime horario, String celebracao, List<EscalaVagaResponse> vagas) {

    public static EscalaEventoResponse de(EscalaEvento e) {
        return new EscalaEventoResponse(
                e.getId(), e.getData(), e.getHorario(), e.getCelebracao(),
                e.getVagas().stream().map(EscalaVagaResponse::de).toList());
    }
}
