package br.com.servire.api.billing.dto;

import br.com.servire.api.billing.Plano;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Plano do catálogo com o preço vigente hoje de cada periodicidade
 * ({@code null} = ainda sem preço) e o histórico completo.
 */
public record PlanoResponse(
        UUID id,
        String codigo,
        String nome,
        Integer limiteVoluntarios,
        boolean ativo,
        BigDecimal precoMensal,
        BigDecimal precoAnual,
        List<PrecoResponse> precos
) {

    public static PlanoResponse de(Plano p, BigDecimal precoMensal, BigDecimal precoAnual, List<PrecoResponse> precos) {
        return new PlanoResponse(p.getId(), p.getCodigo(), p.getNome(), p.getLimiteVoluntarios(), p.isAtivo(),
                precoMensal, precoAnual, precos);
    }
}
