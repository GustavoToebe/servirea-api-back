package br.com.servire.api.diocese.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Cota nula = sem teto de servidores ativos. */
public record DioceseRequest(
        @NotBlank String nome,
        @Size(max = 2) String uf,
        @Positive Integer cotaVoluntarios
) {
}
