package br.com.servire.api.voluntario.dto;

import br.com.servire.api.voluntario.Periodo;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * {@code diaSemana}/{@code data} não levam {@code @NotNull} de propósito —
 * exatamente um dos dois precisa vir preenchido (nunca os dois, nunca
 * nenhum), regra validada em
 * {@link br.com.servire.api.voluntario.DisponibilidadeVoluntarioService#criar}
 * (não uma validação de campo simples de {@code @Valid}).
 */
public record DisponibilidadeVoluntarioRequest(DayOfWeek diaSemana, LocalDate data, @NotNull Periodo periodo,
                                                String observacao) {
}
