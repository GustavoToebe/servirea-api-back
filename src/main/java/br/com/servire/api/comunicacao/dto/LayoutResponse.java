package br.com.servire.api.comunicacao.dto;

import br.com.servire.api.comunicacao.Layout;
import br.com.servire.api.comunicacao.TipoEnvio;
import br.com.servire.api.comunicacao.TipoLayout;

import java.util.UUID;

public record LayoutResponse(
        UUID id,
        String nome,
        TipoLayout tipoLayout,
        TipoEnvio tipoEnvio,
        String assunto,
        String conteudo,
        boolean ativo
) {
    public static LayoutResponse de(Layout l) {
        return new LayoutResponse(
                l.getId(),
                l.getNome(),
                l.getTipoLayout(),
                l.getTipoEnvio(),
                l.getAssunto(),
                l.getConteudo(),
                l.isAtivo()
        );
    }
}
