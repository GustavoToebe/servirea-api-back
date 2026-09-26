package br.com.servire.api.escala.dto;

import br.com.servire.api.escala.Escala;
import br.com.servire.api.escala.StatusEscala;
import br.com.servire.api.escala.TipoEscala;

import java.util.List;
import java.util.UUID;

public record EscalaResponse(
        UUID id,
        Long sequencial,
        String titulo,
        TipoEscala tipo,
        Integer ano,
        Integer mes,
        StatusEscala status,
        String observacao,
        UUID createdBy,
        Long version,
        List<EscalaEventoResponse> eventos) {

    public static EscalaResponse de(Escala e) {
        return new EscalaResponse(
                e.getId(), e.getSequencial(), e.getTitulo(), e.getTipo(), e.getAno(), e.getMes(), e.getStatus(), e.getObservacao(),
                e.getCreatedBy(), e.getVersion(), e.getEventos().stream().map(EscalaEventoResponse::de).toList());
    }
}
