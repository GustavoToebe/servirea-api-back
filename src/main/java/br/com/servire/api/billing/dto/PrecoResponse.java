package br.com.servire.api.billing.dto;

import br.com.servire.api.billing.Periodicidade;
import br.com.servire.api.billing.PrecoPlano;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PrecoResponse(UUID id, Periodicidade periodicidade, BigDecimal valor, LocalDate vigenteDesde) {

    public static PrecoResponse de(PrecoPlano p) {
        return new PrecoResponse(p.getId(), p.getPeriodicidade(), p.getValor(), p.getVigenteDesde());
    }
}
