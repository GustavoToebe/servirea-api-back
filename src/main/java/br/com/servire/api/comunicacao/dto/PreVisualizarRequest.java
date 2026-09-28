package br.com.servire.api.comunicacao.dto;

import br.com.servire.api.comunicacao.TipoEnvio;
import br.com.servire.api.comunicacao.TipoLayout;
import jakarta.validation.constraints.NotNull;

public record PreVisualizarRequest(
        @NotNull TipoLayout tipoLayout,
        @NotNull TipoEnvio tipoEnvio,
        String assunto,
        String conteudo
) {
}
