package br.com.servire.api.escala;

import br.com.servire.api.voluntario.FuncaoEscala;

/** Uma coluna do layout (ou da cópia gravada na escala): ordem na grade, função, posição e rótulo. */
public record ColunaEscalaDto(Integer ordem, FuncaoEscala funcao, int posicao, String rotulo) {
}
