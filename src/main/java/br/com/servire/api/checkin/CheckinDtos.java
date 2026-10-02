package br.com.servire.api.checkin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class CheckinDtos {
    private CheckinDtos() {}

    public record Abrir(@Min(15) @Max(480) Integer minutos) {}

    /** O token aparece só nesta resposta; depois só o servidor conhece o hash. */
    public record Aberto(UUID sessaoId, String token, Instant expiraEm) {}

    public record Registrar(@NotBlank @Size(min = 20, max = 80) String token) {}

    public record Presente(String nome, String funcao, Instant registradoEm) {}

    public record Estado(boolean ativa, Instant expiraEm, int escalados, int presentes, List<Presente> registros) {}

    public record Resultado(String celebracao, LocalDate data, LocalTime horario, String funcao, boolean jaRegistrado) {}
}
