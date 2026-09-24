package br.com.servire.api.escala.dto;

import br.com.servire.api.escala.EscalaVaga;
import br.com.servire.api.escala.Presenca;
import br.com.servire.api.voluntario.FuncaoEscala;

import java.util.UUID;

public record EscalaVagaResponse(UUID id, FuncaoEscala funcao, int posicao, UUID voluntarioId, String voluntarioNome,
                                  Presenca presenca) {

    public static EscalaVagaResponse de(EscalaVaga v) {
        return new EscalaVagaResponse(
                v.getId(),
                v.getFuncao(),
                v.getPosicao(),
                v.getVoluntario() != null ? v.getVoluntario().getId() : null,
                v.getVoluntario() != null ? v.getVoluntario().getPessoa().getNomeCompleto() : null,
                v.getPresenca());
    }
}
