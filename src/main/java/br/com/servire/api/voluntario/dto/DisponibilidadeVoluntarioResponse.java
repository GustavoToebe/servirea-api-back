package br.com.servire.api.voluntario.dto;

import br.com.servire.api.voluntario.DisponibilidadeVoluntario;
import br.com.servire.api.voluntario.Periodo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

public record DisponibilidadeVoluntarioResponse(UUID id, UUID voluntarioId, DayOfWeek diaSemana, LocalDate data,
                                                 Periodo periodo, String observacao) {

    public static DisponibilidadeVoluntarioResponse de(DisponibilidadeVoluntario d) {
        return new DisponibilidadeVoluntarioResponse(
                d.getId(), d.getVoluntario().getId(), d.getDiaSemana(), d.getData(), d.getPeriodo(), d.getObservacao());
    }
}
