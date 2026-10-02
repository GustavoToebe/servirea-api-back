package br.com.servire.api.notificacao;

import br.com.servire.api.comunicacao.TipoEnvio;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificacaoDtos {
    private NotificacaoDtos() {}

    public record Config(OrigemNotificacao origem, TipoEnvio canal, boolean ativo, long versao) {}

    public record Configurar(boolean ativo, @NotNull @Min(0) Long versao) {}

    public record EscalaRequest(@NotNull TipoEnvio canal) {}

    public record AvisoRequest(@NotNull TipoEnvio canal, @NotNull @Min(0) Long versao) {}

    public record Resultado(UUID entregaId, int total, int ignorados) {}

    public record Entrega(UUID id, OrigemNotificacao origem, UUID referenciaId, String titulo, long referenciaVersao,
                          TipoEnvio canal, NotificacaoEntrega.Gatilho gatilho, int total, int ignorados,
                          long pendentes, long enviados, long falhas, Instant criadoEm) {}

    public record Pagina(List<Entrega> itens, long total, int pagina, int tamanho) {}
}
