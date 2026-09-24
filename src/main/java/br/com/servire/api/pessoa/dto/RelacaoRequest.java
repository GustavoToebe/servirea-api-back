package br.com.servire.api.pessoa.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Uma relação vista <b>da pessoa que está sendo salva</b> — mesma regra nas
 * duas listas de {@link PessoaRequest} ({@code responsaveis} e
 * {@code dependentes}) e na resposta ({@link RelacaoResponse}):
 * {@code parentesco} é o que a <b>outra</b> pessoa é (Mãe, na lista de
 * responsáveis; Filho, na de dependentes) e {@code parentescoInverso} é o
 * que <b>esta</b> pessoa é para ela. Assim o front reenvia exatamente o
 * que recebeu sem inverter nada (bug de 24/09/2026: salvar a ficha de um
 * responsável trocava "Mãe"/"Filho" a cada gravação).
 *
 * <p>Informe {@code pessoaId} (pessoa já cadastrada) <b>ou</b>
 * {@code novaPessoa} (criada na mesma transação, só na lista de
 * responsáveis — se o resto do cadastro falhar, ela não fica órfã).</p>
 */
public record RelacaoRequest(
        UUID pessoaId,
        @Valid NovaPessoaRequest novaPessoa,
        @NotBlank String parentesco,
        String parentescoInverso,
        boolean principal) {

    public RelacaoRequest(UUID pessoaId, String parentesco, String parentescoInverso, boolean principal) {
        this(pessoaId, null, parentesco, parentescoInverso, principal);
    }
}
