package br.com.servire.api.pessoa.dto;

import br.com.servire.api.pessoa.PessoaPapel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Cadastro pessoa-primeiro, na ordem da tela: identidade, e-mails,
 * telefones, relações, endereço, observações, bloco de voluntário.
 * {@code papeis} aceita um ou os dois (adulto voluntário + responsável).
 *
 * <p>Relações em duas listas, uma por lado (antes era uma só,
 * {@code relacoes}, e quem tinha os dois papéis não conseguia salvar a
 * ficha): {@code responsaveis} = quem responde por esta pessoa (exige
 * papel VOLUNTARIO); {@code dependentes} = por quem esta pessoa responde
 * (exige papel RESPONSAVEL). Lista nula ou vazia remove as relações
 * daquele lado.</p>
 */
public record PessoaRequest(
        @NotEmpty Set<PessoaPapel> papeis,
        @NotBlank String nomeCompleto,
        LocalDate dataNascimento,
        String sexo,
        String cpf,
        String rg,
        List<@Valid ContatoEmailRequest> emails,
        List<@Valid ContatoTelefoneRequest> telefones,
        List<@Valid RelacaoRequest> responsaveis,
        List<@Valid RelacaoRequest> dependentes,
        String cep,
        String cidade,
        String uf,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String observacoes,
        List<br.com.servire.api.pessoa.CondicaoEspecial> condicoes,
        Integer nivelSuporteTea,
        String condicaoOutra,
        @jakarta.validation.constraints.Size(max = 1000) String cuidados,
        @Valid VoluntarioPerfilRequest voluntario) {
}
