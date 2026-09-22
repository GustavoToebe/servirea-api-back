package br.com.servire.api.voluntario.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Um responsável dentro do payload de {@code POST}/{@code PUT
 * /voluntarios} (seção 38). A regra "exatamente um {@code principal}"
 * não dá para validar campo a campo aqui — é validada em
 * {@code VoluntarioService} olhando a lista inteira.
 */
public record ResponsavelRequest(
        @NotBlank String parentesco,
        @NotBlank String nome,
        String telefone,
        String celular,
        String email,
        boolean principal) {
}
