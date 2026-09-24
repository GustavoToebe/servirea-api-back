package br.com.servire.api.voluntario.dto;

import br.com.servire.api.pessoa.dto.PessoaResponse;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.voluntario.Voluntario;

import java.util.List;
import java.util.UUID;

/** Lista/detalhe de serviço. Identidade completa em {@link PessoaResponse}. */
public record VoluntarioResponse(
        UUID id,
        String nomeCompleto,
        TipoVoluntario tipo,
        boolean ativo,
        String fotoPath,
        String etapaCatequese,
        String eucaristiaAno,
        String crismaAno,
        Voluntario.HorarioEstudo horarioEstudo,
        boolean autorizaWhatsapp,
        List<FuncaoEscala> funcoesHabilitadas) {

    public static VoluntarioResponse de(Voluntario v) {
        return new VoluntarioResponse(
                v.getId(),
                v.getPessoa().getNomeCompleto(),
                v.getTipo(),
                v.isAtivo(),
                v.getFotoPath(),
                v.getEtapaCatequese(),
                v.getEucaristiaAno(),
                v.getCrismaAno(),
                v.getHorarioEstudo(),
                v.isAutorizaWhatsapp(),
                List.of(v.getFuncoesHabilitadas()));
    }
}
