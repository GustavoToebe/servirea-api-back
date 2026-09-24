package br.com.servire.api.escala.dto;

import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.voluntario.Voluntario;

import java.util.List;
import java.util.UUID;

/** Item do picker de candidatos (seção 49) — sem responsáveis (coleção LAZY). */
public record CandidatoResponse(
        UUID id,
        String nomeCompleto,
        TipoVoluntario tipo,
        String fotoPath,
        List<FuncaoEscala> funcoesHabilitadas) {

    public static CandidatoResponse de(Voluntario v) {
        FuncaoEscala[] funcoes = v.getFuncoesHabilitadas();
        return new CandidatoResponse(
                v.getId(),
                v.getPessoa().getNomeCompleto(),
                v.getTipo(),
                v.getFotoPath(),
                funcoes == null ? List.of() : List.of(funcoes));
    }
}
