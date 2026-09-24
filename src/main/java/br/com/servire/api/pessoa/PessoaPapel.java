package br.com.servire.api.pessoa;

/**
 * Papel exclusivo da {@link Pessoa}: ou voluntário ou responsável.
 * Imutável depois de criado — trocar quebraria escala e relação.
 */
public enum PessoaPapel {
    VOLUNTARIO,
    RESPONSAVEL
}
