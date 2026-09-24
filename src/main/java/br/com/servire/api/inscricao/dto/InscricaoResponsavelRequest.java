package br.com.servire.api.inscricao.dto;

import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** Responsável do formulário público — identidade + listas 1:N + rótulos é/de. */
public record InscricaoResponsavelRequest(
        @NotBlank String parentesco,
        String parentescoInverso,
        @NotBlank String nome,
        List<@Valid ContatoEmailRequest> emails,
        List<@Valid ContatoTelefoneRequest> telefones,
        boolean principal) {
}
