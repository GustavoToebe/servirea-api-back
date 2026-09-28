package br.com.servire.api.escala.dto;

import br.com.servire.api.voluntario.Periodo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contratos das indisponibilidades do mês e do apoio à montagem (PLANO-007). */
public final class IndisponibilidadeDtos {

    private IndisponibilidadeDtos() {
    }

    /** {@code periodo} nulo = o dia inteiro. */
    public record Item(@NotNull UUID voluntarioId, @NotNull LocalDate data, Periodo periodo, @Size(max = 200) String observacao) {
    }

    public record MesRequest(@NotNull List<@Valid Item> itens, @NotNull List<UUID> semRestricao) {
    }

    public record MesResponse(int ano, int mes, List<Item> itens, List<UUID> semRestricao) {
    }

    public enum Situacao { COM_RESTRICAO, SEM_RESTRICAO, PENDENTE }

    public record Indisponivel(LocalDate data, Periodo periodo) {
    }

    public record ApoioVoluntario(UUID voluntarioId, Situacao situacao, List<Indisponivel> indisponiveis, List<UUID> irmaos) {
    }

    public record ApoioEscala(List<ApoioVoluntario> voluntarios) {
    }
}
