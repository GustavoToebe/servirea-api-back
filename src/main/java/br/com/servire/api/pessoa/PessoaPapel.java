package br.com.servire.api.pessoa;

/**
 * Papel da {@link Pessoa}. Não é exclusivo: um adulto pode ser
 * voluntário (ministro, coroinha maior) e responsável por outro
 * voluntário ao mesmo tempo. O cadastro interno não exige responsável.
 */
public enum PessoaPapel {
    VOLUNTARIO,
    RESPONSAVEL
}
