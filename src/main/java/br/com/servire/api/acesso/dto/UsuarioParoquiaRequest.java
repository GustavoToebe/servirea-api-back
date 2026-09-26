package br.com.servire.api.acesso.dto;

import br.com.servire.api.web.Formatos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UsuarioParoquiaRequest(
        @NotBlank String nome,
        @NotBlank @Email(regexp = Formatos.EMAIL, message = "E-mail inválido.") String email,
        String tipoTelefone,
        String telefone,
        @NotNull UUID perfilId,
        boolean ativo
) {
}
