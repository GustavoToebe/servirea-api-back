package br.com.servire.api.inscricao.dto;

import br.com.servire.api.inscricao.Inscricao;
import br.com.servire.api.inscricao.StatusInscricao;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.voluntario.Voluntario;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InscricaoResponse(
        UUID id,
        String nomeCompleto,
        LocalDate dataNascimento,
        TipoVoluntario tipo,
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
        StatusInscricao status,
        Instant dataAprovacao,
        UUID aprovadoPor,
        UUID voluntarioId,
        Instant dataRejeicao,
        UUID rejeitadoPor,
        String motivoRejeicao,
        List<InscricaoResponsavelResponse> responsaveis) {

    public static InscricaoResponse de(Inscricao i) {
        return new InscricaoResponse(
                i.getId(),
                i.getNomeCompleto(),
                i.getDataNascimento(),
                i.getTipo(),
                i.getFotoPath(),
                i.getEtapaCatequese(),
                i.getEucaristiaAno(),
                i.getCrismaAno(),
                i.getRua(),
                i.getNumero(),
                i.getBairro(),
                i.getTelefone(),
                i.getCelular(),
                i.getEmail(),
                i.getHorarioEstudo(),
                i.getObservacoes(),
                i.isAutorizaWhatsapp(),
                List.of(i.getFuncoesHabilitadas()),
                i.getStatus(),
                i.getDataAprovacao(),
                i.getAprovadoPor(),
                i.getVoluntarioId(),
                i.getDataRejeicao(),
                i.getRejeitadoPor(),
                i.getMotivoRejeicao(),
                i.getResponsaveis().stream().map(InscricaoResponsavelResponse::de).toList());
    }
}
