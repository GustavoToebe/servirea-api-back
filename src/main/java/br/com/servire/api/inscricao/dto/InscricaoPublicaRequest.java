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
 * Payload de {@code POST /public/{tenantSlug}/inscricoes} (Fase 8, seção
 * 44/108) — vem como a parte {@code dados} de um
 * {@code multipart/form-data} (a foto, se enviada, vem na parte
 * {@code foto} separadamente; ver {@code PublicInscricaoController}).
 *
 * <p>{@code turnstileToken} é o token que o widget do Cloudflare Turnstile
 * gera no navegador do candidato — validado por
 * {@link br.com.servire.api.inscricao.TurnstileService} ANTES de qualquer
 * gravação no banco (falha fechado, ver javadoc daquela classe).</p>
 */
public record InscricaoPublicaRequest(
        @NotBlank String turnstileToken,
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
