package br.com.servire.api.escala.dto;

import br.com.servire.api.escala.TipoEscala;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import br.com.servire.api.escala.ColunaEscalaDto;

import java.util.List;
import java.util.UUID;

/**
 * Payload de {@code POST}/{@code PUT /escalas} (Fase 9, seção 46/109).
 *
 * <p>{@code version} só é usado (e obrigatório) no {@code PUT} — controle
 * otimista (seção 47): o cliente reenvia a versão que leu por último, e
 * {@code EscalaService} recusa a atualização com HTTP 409 se ela não bater
 * com a versão atual no banco. No {@code POST} (criação) é ignorado.</p>
 *
 * <p>{@code eventos} é a lista COMPLETA desejada após a operação — mesmo
 * padrão "apaga tudo e reinsere" já usado para responsáveis na Fase 6,
 * preservando o comportamento atual do Angular (ver javadoc de
 * {@link br.com.servire.api.escala.EscalaEvento}).</p>
 */
public record EscalaRequest(
        @NotBlank String titulo,
        @NotNull TipoEscala tipo,
        @Min(2020) @Max(2100) Integer ano,
        @Min(1) @Max(12) Integer mes,
        String observacao,
        UUID layoutId,
        List<ColunaEscalaDto> colunas,
        Long version,
        @NotEmpty List<@Valid EscalaEventoRequest> eventos) {
        
    public EscalaRequest(String titulo, TipoEscala tipo, Integer ano, Integer mes, String observacao, Long version, List<EscalaEventoRequest> eventos) {
        this(titulo, tipo, ano, mes, observacao, null, null, version, eventos);
    }

    public EscalaRequest(String titulo, TipoEscala tipo, Integer ano, Integer mes, List<EscalaEventoRequest> eventos) {
        this(titulo, tipo, ano, mes, null, null, null, null, eventos);
    }
}
