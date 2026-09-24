package br.com.servire.api.inscricao.dto;

import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.voluntario.Voluntario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** PUT interno de inscrição ainda PENDENTE — igual ao público, sem Turnstile. */
public record InscricaoAtualizarRequest(
        @NotBlank String nomeCompleto,
        LocalDate dataNascimento,
        String sexo,
        String cpf,
        String rg,
        @NotNull TipoVoluntario tipo,
        String etapaCatequese,
        String eucaristiaAno,
        String crismaAno,
        @Valid List<ContatoEmailRequest> emails,
        @Valid List<ContatoTelefoneRequest> telefones,
        @Valid List<InscricaoResponsavelRequest> responsaveis,
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
        List<FuncaoEscala> funcoesHabilitadas) {
}
