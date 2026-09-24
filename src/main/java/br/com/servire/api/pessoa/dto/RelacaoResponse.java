package br.com.servire.api.pessoa.dto;

import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaPapel;
import br.com.servire.api.pessoa.PessoaRelacao;

import java.util.Set;
import java.util.UUID;

public record RelacaoResponse(
        UUID id,
        UUID pessoaId,
        String nomeCompleto,
        Set<PessoaPapel> papeisOutro,
        String parentesco,
        String parentescoInverso,
        boolean principal) {

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
                r.getParentescoInverso() == null ? r.getParentesco() : r.getParentescoInverso(),
                r.getParentesco(), r.isPrincipal());
    }
}
