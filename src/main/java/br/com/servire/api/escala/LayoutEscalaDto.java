package br.com.servire.api.escala;

import java.util.List;
import java.util.UUID;

/**
 * `ativo` e `padrao` são {@code Boolean}: campo ausente no JSON (o editor antigo mandava só nome e tipo) não pode virar
 * "false" e desativar o layout sem querer. Ausente = "não mexer" (e "ativo" ao criar).
 */
public record LayoutEscalaDto(UUID id, String nome, TipoEscala tipo, List<ColunaEscalaDto> colunas, Boolean ativo,
                              boolean sistema, String descricao, Boolean padrao) {
    public static LayoutEscalaDto de(LayoutEscala l) {
        return new LayoutEscalaDto(l.getId(), l.getNome(), l.getTipo(), l.getColunas(), l.isAtivo(), l.isSistema(),
                l.getDescricao(), l.isPadrao());
    }
}
