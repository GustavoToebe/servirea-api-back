package br.com.servire.api.escala;

import br.com.servire.api.voluntario.FuncaoEscala;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ColunaEscalaDto(
    Integer ordem, 
    FuncaoEscala funcao, 
    int posicao, 
    String rotulo,
    String tipo,
    String idLocal,
    String conteudo,
    String alinhamento,
    Boolean negrito,
    Integer linha,
    Integer coluna,
    Integer largura,
    String escopo
) {

    /** Coluna de vaga simples (o formato anterior ao editor visual). */
    public ColunaEscalaDto(Integer ordem, FuncaoEscala funcao, int posicao, String rotulo) {
        this(ordem, funcao, posicao, rotulo, null, null, null, null, null, null, null, null, null);
    }
}
