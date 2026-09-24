package br.com.servire.api.pessoa.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Responsável cadastrado "de passagem" na ficha do voluntário: só nome e,
 * opcionalmente, e-mail e telefone principais. O resto se completa depois
 * na ficha dele.
 */
public record NovaPessoaRequest(
        @NotBlank String nomeCompleto,
        @Email String email,
        String telefone) {
}
