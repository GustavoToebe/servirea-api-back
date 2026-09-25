package br.com.servire.api.backoffice.dto;

import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** PUT do operador — não mexe em codigo/slug/status. */
public record AtualizarParoquiaRequest(
        @NotBlank String nome,
        String razaoSocial,
        String cnpj,
        List<@Valid ContatoEmailRequest> emails,
        List<@Valid ContatoTelefoneRequest> telefones,
        String cep,
        String cidade,
        String uf,
        String bairro,
        String logradouro,
        String numero,
        String complemento,
        String observacoes,
        LocalDate vigenciaAte,
        UUID dioceseId
) {
}
