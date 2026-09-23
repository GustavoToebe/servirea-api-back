package br.com.servire.api.backoffice.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** PUT do operador — não mexe em codigo/slug/status (status tem endpoints próprios). */
public record AtualizarParoquiaRequest(
        @NotBlank String nome,
        String razaoSocial,
        String cnpj,
        String email,
        String telefone,
        String cep,
        String cidade,
        String uf,
        String bairro,
        String logradouro,
        String numero,
        String complemento,
        String observacoes,
        String tipoEmail,
        LocalDate vigenciaAte
) {
}
