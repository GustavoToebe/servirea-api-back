package br.com.servire.api.billing.dto;

import br.com.servire.api.billing.Periodicidade;

/**
 * Resumo financeiro de uma paróquia para a listagem do backoffice
 * (colunas Plano e "Em atraso"). Montado para todas as paróquias com
 * duas queries, sem N+1.
 */
public record SituacaoFinanceira(
        String planoNome,
        Periodicidade periodicidade,
        long cobrancasVencidas,
        long diasAtraso
) {

    public static final SituacaoFinanceira VAZIA = new SituacaoFinanceira(null, null, 0, 0);
}
