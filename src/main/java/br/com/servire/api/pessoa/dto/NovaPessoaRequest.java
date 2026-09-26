package br.com.servire.api.pessoa.dto;

import br.com.servire.api.web.Formatos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Responsável cadastrado "de passagem" na ficha do voluntário: só nome e,
 * opcionalmente, e-mail e telefone principais. O resto se completa depois
 * na ficha dele.
 */
public record NovaPessoaRequest(
        @NotBlank String nomeCompleto,
        @Email(regexp = Formatos.EMAIL, message = "E-mail inválido.") String email,
        String telefone) {
}
