package br.com.servire.api.escala;

import java.util.List;
import java.util.UUID;

public record LayoutEscalaDto(UUID id, String nome, TipoEscala tipo, List<ColunaEscalaDto> colunas, boolean ativo, boolean sistema) {
    public static LayoutEscalaDto de(LayoutEscala l) {
        return new LayoutEscalaDto(l.getId(), l.getNome(), l.getTipo(), l.getColunas(), l.isAtivo(), l.isSistema());
    }
}
