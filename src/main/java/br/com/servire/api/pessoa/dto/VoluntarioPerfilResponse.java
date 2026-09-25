package br.com.servire.api.pessoa.dto;

import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.voluntario.Voluntario;

import java.time.LocalDate;
import java.util.List;

public record VoluntarioPerfilResponse(
        TipoVoluntario tipo,
        boolean ativo,
        String fotoPath,
        String etapaCatequese,
        String eucaristiaAno,
        String crismaAno,
        Voluntario.HorarioEstudo horarioEstudo,
        boolean autorizaWhatsapp,
        List<FuncaoEscala> funcoesHabilitadas,
        LocalDate mandatoInicio,
        LocalDate mandatoFim) {

    public static VoluntarioPerfilResponse de(Voluntario v) {
        if (v == null) {
            return null;
        }
        return new VoluntarioPerfilResponse(
                v.getTipo(), v.isAtivo(), v.getFotoPath(), v.getEtapaCatequese(),
                v.getEucaristiaAno(), v.getCrismaAno(), v.getHorarioEstudo(),
                v.isAutorizaWhatsapp(), List.of(v.getFuncoesHabilitadas()),
                v.getMandatoInicio(), v.getMandatoFim());
    }
}
