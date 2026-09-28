package br.com.servire.api.comunicacao.dto;

import br.com.servire.api.comunicacao.TipoEnvio;
import br.com.servire.api.comunicacao.TipoLayout;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LayoutRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotNull TipoLayout tipoLayout,
        @NotNull TipoEnvio tipoEnvio,
        @Size(max = 200) String assunto,
        @NotBlank @Size(max = 50000) String conteudo,
        boolean ativo
) {
}
