package br.com.servire.api.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tela "Financeiro" da paróquia no backoffice: contrato atual, histórico
 * de contratos, todas as cobranças (mais recente primeiro) e o resumo.
 */
public record FinanceiroResponse(
        AssinaturaResponse assinaturaAtual,
        List<AssinaturaResponse> assinaturas,
        List<CobrancaResponse> cobrancas,
        Resumo resumo
) {

    /**
     * @param vencidas           cobranças em aberto com vencimento passado
     * @param diasAtraso         dias desde o vencimento mais antigo em aberto (0 = em dia)
     * @param valorEmAtraso      soma das vencidas
     * @param proximoVencimento  próxima cobrança em aberto ainda não vencida
     * @param totalPagoNoAno     soma de {@code valor_pago} com pagamento no ano corrente
     */
    public record Resumo(
            int vencidas,
            long diasAtraso,
            BigDecimal valorEmAtraso,
            LocalDate proximoVencimento,
            BigDecimal totalPagoNoAno
    ) {
    }
}
