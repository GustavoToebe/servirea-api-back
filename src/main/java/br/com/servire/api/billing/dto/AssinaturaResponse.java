package br.com.servire.api.billing.dto;

import br.com.servire.api.billing.Assinatura;
import br.com.servire.api.billing.Periodicidade;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AssinaturaResponse(
        UUID id,
        UUID planoId,
        String planoNome,
        Periodicidade periodicidade,
        BigDecimal valor,
        int diaVencimento,
        LocalDate inicio,
        LocalDate fim,
        Assinatura.Status status,
        String observacoes
) {

    public static AssinaturaResponse de(Assinatura a) {
        return new AssinaturaResponse(a.getId(), a.getPlano().getId(), a.getPlano().getNome(),
                a.getPeriodicidade(), a.getValor(), a.getDiaVencimento(), a.getInicio(), a.getFim(),
                a.getStatus(), a.getObservacoes());
    }
}
