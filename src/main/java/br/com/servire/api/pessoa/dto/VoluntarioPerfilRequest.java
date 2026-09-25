package br.com.servire.api.pessoa.dto;

import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.voluntario.Voluntario;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** Bloco de serviço — obrigatório quando {@code papeis} contém VOLUNTARIO. */
public record VoluntarioPerfilRequest(
        @NotNull TipoVoluntario tipo,
        boolean ativo,
        String etapaCatequese,
        String eucaristiaAno,
        String crismaAno,
        Voluntario.HorarioEstudo horarioEstudo,
        boolean autorizaWhatsapp,
        List<FuncaoEscala> funcoesHabilitadas,
        LocalDate mandatoInicio,
        LocalDate mandatoFim) {
}
