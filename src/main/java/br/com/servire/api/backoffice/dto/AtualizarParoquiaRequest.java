package br.com.servire.api.backoffice.dto;

import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

/** PUT do operador — não mexe em codigo/slug/status. */
public record AtualizarParoquiaRequest(
        @NotBlank String nome,
        String razaoSocial,
        String cnpj,
        @Valid List<ContatoEmailRequest> emails,
        @Valid List<ContatoTelefoneRequest> telefones,
        String cep,
        String cidade,
        String uf,
        String bairro,
        String logradouro,
        String numero,
        String complemento,
        String observacoes,
        LocalDate vigenciaAte
) {
}
