package br.com.servire.api.acesso.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record PerfilRequest(
        @NotBlank String nome,
        boolean ativo,
        boolean acessoTotal,
        List<String> permissoes
) {
}
