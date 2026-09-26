package br.com.servire.api.pessoa.dto;

import br.com.servire.api.web.Formatos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Uma linha de e-mail (tipo livre: "E-mail pessoal", "Secundário"…). */
public record ContatoEmailRequest(
        @NotBlank String tipo,
        @NotBlank @Email(regexp = Formatos.EMAIL, message = "E-mail inválido.") String email,
        boolean principal) {
}
