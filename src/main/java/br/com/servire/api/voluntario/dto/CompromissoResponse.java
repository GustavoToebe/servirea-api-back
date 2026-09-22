package br.com.servire.api.voluntario.dto;

import br.com.servire.api.escala.Escala;
import br.com.servire.api.escala.EscalaEvento;
import br.com.servire.api.escala.EscalaVaga;
import br.com.servire.api.escala.StatusEscala;
import br.com.servire.api.voluntario.FuncaoEscala;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Um compromisso do voluntário — mesmo conteúdo de
 * {@code vw_voluntario_compromissos} (V010), montado via JPA para não
 * bypassar o {@code @TenantId} (Native Query Gate, seção 81).
 */
public record CompromissoResponse(
        UUID voluntarioId,
        UUID escalaId,
        String escalaTitulo,
        StatusEscala escalaStatus,
        LocalDate data,
        LocalTime horario,
        String celebracao,
        FuncaoEscala funcao) {

    public static CompromissoResponse de(EscalaVaga vaga) {
        EscalaEvento evento = vaga.getEvento();
        Escala escala = evento.getEscala();
        return new CompromissoResponse(
                vaga.getVoluntario().getId(),
                escala.getId(),
                escala.getTitulo(),
                escala.getStatus(),
                evento.getData(),
                evento.getHorario(),
                evento.getCelebracao(),
                vaga.getFuncao());
    }
}
