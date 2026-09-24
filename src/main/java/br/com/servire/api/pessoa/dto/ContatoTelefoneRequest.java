package br.com.servire.api.pessoa.dto;

import jakarta.validation.constraints.NotBlank;

/** Uma linha de telefone (tipo livre: "Telefone pessoal", "celular"…). */
public record ContatoTelefoneRequest(
        @NotBlank String tipo,
        @NotBlank String numero,
        boolean principal) {
}
