package br.com.servire.api.escala.dto;

import br.com.servire.api.voluntario.FuncaoEscala;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Contratos da distribuição por regras (F04). Nada daqui grava sem a aplicação explícita. */
public final class DistribuicaoDtos {
    private DistribuicaoDtos() {}

    public record Regras(@Min(1) @Max(20) int maximoPorPessoa, @Min(0) @Max(30) int intervaloDias, boolean exigirResposta) {}

    public record PreviaRequest(@NotNull @Valid Regras regras) {}

    public record Escolha(@NotNull UUID vagaId, @NotNull UUID pessoaId) {}

    public record AplicarRequest(@NotNull @Min(0) Long versao, @NotNull @Valid Regras regras,
                                 @NotEmpty @Size(max = 500) List<@Valid @NotNull Escolha> escolhas) {}

    public record Sugestao(UUID vagaId, UUID eventoId, LocalDate data, LocalTime horario, String celebracao,
                           FuncaoEscala funcao, UUID pessoaId, String nome, String explicacao) {}

    public record Descarte(String codigo, String motivo, int quantidade) {}

    public record Conflito(UUID vagaId, UUID eventoId, LocalDate data, LocalTime horario, String celebracao,
                           FuncaoEscala funcao, String explicacao, List<Descarte> descartes, boolean alocacaoExistente) {}

    public record Previa(UUID escalaId, long versao, int vagasVazias, List<Sugestao> sugestoes,
                         List<Conflito> conflitos, boolean bloqueado) {}

    public record Aplicada(UUID escalaId, long versao, int aplicadas) {}
}
