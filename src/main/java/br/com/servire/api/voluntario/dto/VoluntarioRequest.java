package br.com.servire.api.voluntario.dto;

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
 * Payload de {@code POST}/{@code PUT /voluntarios} (seção 37/38/106).
 * Usado tanto para criar quanto para atualizar — em ambos os casos
 * {@code responsaveis} é a lista COMPLETA desejada após a operação: o
 * serviço substitui todos os responsáveis existentes por esta lista
 * (mesmo padrão "apaga tudo e reinsere" que o Angular atual usa via
 * {@code replaceResponsaveis}, ver seção 10 do documento técnico), não
 * um PATCH incremental.
 *
 * <p>Upload de foto (Storage, Fase 7) fica fora deste payload — por
 * enquanto {@code fotoPath} não é setável por aqui; a Fase 7 decide como
 * a referência do arquivo entra no voluntário.</p>
 */
public record VoluntarioRequest(
        @NotBlank String nomeCompleto,
        LocalDate dataNascimento,
        @NotNull TipoVoluntario tipo,
        boolean ativo,
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
        @NotEmpty @Valid List<ResponsavelRequest> responsaveis) {
}
