package br.com.servire.api.inscricao.dto;

import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.voluntario.Voluntario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Payload de {@code PUT /inscricoes/{id}} (Fase 8) — edição interna de uma
 * inscrição ainda {@code PENDENTE} pelo coordenador, antes de
 * aprovar/rejeitar (ex.: corrigir um dado que o candidato digitou errado
 * no formulário público). Igual a {@link InscricaoPublicaRequest} menos
 * {@code turnstileToken} — não faz sentido reverificar Turnstile numa
 * edição feita por um usuário já autenticado.
 */
public record InscricaoAtualizarRequest(
        @NotBlank String nomeCompleto,
        LocalDate dataNascimento,
        @NotNull TipoVoluntario tipo,
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
        @NotEmpty @Valid List<InscricaoResponsavelRequest> responsaveis) {
}
