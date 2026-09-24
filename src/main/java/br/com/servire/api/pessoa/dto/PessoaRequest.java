package br.com.servire.api.pessoa.dto;

import br.com.servire.api.pessoa.PessoaPapel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Cadastro pessoa-primeiro, na ordem da tela: identidade, e-mails,
 * telefones, relações, endereço, observações, bloco de voluntário.
 */
public record PessoaRequest(
        @NotNull PessoaPapel papel,
        @NotBlank String nomeCompleto,
        LocalDate dataNascimento,
        String sexo,
        String cpf,
        String rg,
        @Valid List<ContatoEmailRequest> emails,
        @Valid List<ContatoTelefoneRequest> telefones,
        @Valid List<RelacaoRequest> relacoes,
        String cep,
        String cidade,
        String uf,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String observacoes,
        @Valid VoluntarioPerfilRequest voluntario) {
}
