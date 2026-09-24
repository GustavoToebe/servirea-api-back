package br.com.servire.api.billing;

/**
 * Ciclo de cobrança (seção 131.3: Standard/Pro com opção mensal e anual).
 * Espelha o enum nativo {@code plano_periodicidade} (V029).
 */
public enum Periodicidade {
    MENSAL(1),
    ANUAL(12);

    private final int meses;

    Periodicidade(int meses) {
        this.meses = meses;
    }

    /** Quantos meses uma cobrança desta periodicidade cobre. */
    public int meses() {
        return meses;
    }
}
