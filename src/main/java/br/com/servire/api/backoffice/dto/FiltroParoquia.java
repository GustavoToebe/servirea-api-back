package br.com.servire.api.backoffice.dto;

import br.com.servire.api.backoffice.SituacaoParoquia;
import br.com.servire.api.tenant.Tenant;

import java.time.LocalDate;

/**
 * Query de {@code GET /admin/paroquias}. Todos opcionais; combinados com
 * AND. Espelha o painel de filtros do SIN (busca + situação + datas),
 * mapeado para paróquia — não para voluntário.
 */
public record FiltroParoquia(
        Tenant.Status status,
        SituacaoParoquia situacao,
        String nome,
        String cnpj,
        String email,
        String tipoEmail,
        LocalDate contratadoDe,
        LocalDate contratadoAte,
        LocalDate vigenciaDe,
        LocalDate vigenciaAte,
        /** {@code true} = só paróquias com cobrança vencida em aberto (V029). */
        Boolean emAtraso
) {
}
