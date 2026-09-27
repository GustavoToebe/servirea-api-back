package br.com.servire.api.tenant.dto;

import jakarta.validation.constraints.Size;

/**
 * Endereço da paróquia (27/09/2026). As colunas já existiam desde a V027; a cidade e a UF também vão
 * no subtítulo da lista de assinatura. CEP e UF são normalizados por {@code Formatos}.
 */
public record EnderecoDto(
        @Size(max = 9) String cep,
        @Size(max = 200) String logradouro,
        @Size(max = 20) String numero,
        @Size(max = 100) String complemento,
        @Size(max = 100) String bairro,
        @Size(max = 100) String cidade,
        @Size(max = 2) String uf
) {
}
