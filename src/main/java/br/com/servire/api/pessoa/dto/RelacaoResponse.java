package br.com.servire.api.pessoa.dto;

import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaPapel;
import br.com.servire.api.pessoa.PessoaRelacao;

import java.util.Set;
import java.util.UUID;

/**
 * Relação vista da pessoa consultada: {@code parentesco} é o que a outra
 * pessoa é, {@code parentescoInverso} é o que a pessoa consultada é — ver
 * {@link RelacaoRequest}.
 */
public record RelacaoResponse(
        UUID id,
        UUID pessoaId,
        String nomeCompleto,
        Set<PessoaPapel> papeisOutro,
        String parentesco,
        String parentescoInverso,
        boolean principal) {

    /** Rótulo quando a relação não guardou o lado do voluntário. */
    static final String DEPENDENTE = "Dependente";

    /** Vista do voluntário: o outro lado é o responsável. */
    public static RelacaoResponse doVoluntario(PessoaRelacao r) {
        Pessoa outro = r.getResponsavel();
        return new RelacaoResponse(r.getId(), outro.getId(), outro.getNomeCompleto(), outro.getPapeis(),
                r.getParentesco(), r.getParentescoInverso(), r.isPrincipal());
    }

    /** Vista do responsável: o outro lado é o voluntário. */
    public static RelacaoResponse doResponsavel(PessoaRelacao r) {
        Pessoa outro = r.getVoluntario();
        return new RelacaoResponse(r.getId(), outro.getId(), outro.getNomeCompleto(), outro.getPapeis(),
                r.getParentescoInverso() == null ? DEPENDENTE : r.getParentescoInverso(),
                r.getParentesco(), r.isPrincipal());
    }
}
