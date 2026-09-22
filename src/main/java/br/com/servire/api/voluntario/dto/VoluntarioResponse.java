package br.com.servire.api.voluntario.dto;

import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.voluntario.Voluntario;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VoluntarioResponse(
        UUID id,
        String nomeCompleto,
        LocalDate dataNascimento,
        TipoVoluntario tipo,
        boolean ativo,
        String fotoPath,
        String etapaCatequese,
        String eucaristiaAno,
        String crismaAno,
        String rua,
        String numero,
        String bairro,
        String telefone,
        String celular,
        String email,
        Voluntario.HorarioEstudo horarioEstudo,
        String observacoes,
        boolean autorizaWhatsapp,
        List<FuncaoEscala> funcoesHabilitadas,
        List<ResponsavelResponse> responsaveis) {

    public static VoluntarioResponse de(Voluntario v) {
        return new VoluntarioResponse(
                v.getId(),
                v.getNomeCompleto(),
                v.getDataNascimento(),
                v.getTipo(),
                v.isAtivo(),
                v.getFotoPath(),
                v.getEtapaCatequese(),
                v.getEucaristiaAno(),
                v.getCrismaAno(),
                v.getRua(),
                v.getNumero(),
                v.getBairro(),
                v.getTelefone(),
                v.getCelular(),
                v.getEmail(),
                v.getHorarioEstudo(),
                v.getObservacoes(),
                v.isAutorizaWhatsapp(),
                List.of(v.getFuncoesHabilitadas()),
                v.getResponsaveis().stream().map(ResponsavelResponse::de).toList());
    }
}
