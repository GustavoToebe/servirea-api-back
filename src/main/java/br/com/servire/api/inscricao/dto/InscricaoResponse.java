package br.com.servire.api.inscricao.dto;

import br.com.servire.api.inscricao.Inscricao;
import br.com.servire.api.inscricao.StatusInscricao;
import br.com.servire.api.pessoa.dto.ContatoEmailResponse;
import br.com.servire.api.pessoa.dto.ContatoTelefoneResponse;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.voluntario.Voluntario;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InscricaoResponse(
        UUID id,
        Long sequencial,
        String nomeCompleto,
        LocalDate dataNascimento,
        String sexo,
        String cpf,
        String rg,
        TipoVoluntario tipo,
        String fotoPath,
        String etapaCatequese,
        String eucaristiaAno,
        String crismaAno,
        List<ContatoEmailResponse> emails,
        List<ContatoTelefoneResponse> telefones,
        List<InscricaoResponsavelResponse> responsaveis,
        String cep,
        String cidade,
        String uf,
        String rua,
        String numero,
        String complemento,
        String bairro,
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
        String motivoRejeicao) {

    public static InscricaoResponse de(Inscricao i) {
        return new InscricaoResponse(
                i.getId(),
                i.getSequencial(),
                i.getNomeCompleto(),
                i.getDataNascimento(),
                i.getSexo(),
                i.getCpf(),
                i.getRg(),
                i.getTipo(),
                i.getFotoPath(),
                i.getEtapaCatequese(),
                i.getEucaristiaAno(),
                i.getCrismaAno(),
                i.getEmails().stream()
                        .map(e -> new ContatoEmailResponse(e.getId(), e.getTipo(), e.getEmail(), e.isPrincipal()))
                        .toList(),
                i.getTelefones().stream()
                        .map(t -> new ContatoTelefoneResponse(t.getId(), t.getTipo(), t.getNumero(), t.isPrincipal()))
                        .toList(),
                i.getResponsaveis().stream().map(InscricaoResponsavelResponse::de).toList(),
                i.getCep(),
                i.getCidade(),
                i.getUf(),
                i.getRua(),
                i.getNumero(),
                i.getComplemento(),
                i.getBairro(),
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
                i.getMotivoRejeicao());
    }
}
