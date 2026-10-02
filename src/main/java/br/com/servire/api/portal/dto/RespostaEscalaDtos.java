package br.com.servire.api.portal.dto;

import br.com.servire.api.escala.RespostaParticipacao;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class RespostaEscalaDtos {
    private RespostaEscalaDtos() {}
    public record Responder(@NotNull RespostaParticipacao resposta, @NotNull @Min(0) Long versao) {}
    public record Atual(RespostaParticipacao resposta, Instant respondidoEm, long versao) {}
    public record Historico(UUID id, RespostaParticipacao resposta, Instant respondidoEm, long versao) {}
    public record Item(UUID vagaId, String pessoaNome, String celebracao, LocalDateTime inicio,
                       String funcao, RespostaParticipacao resposta, Instant respondidoEm) {}
    public record Pagina<T>(List<T> itens, long total, int pagina, int tamanho) {}
}
