package br.com.servire.api.backoffice;

import br.com.servire.api.tenant.Tenant;

import java.util.List;

/**
 * Atalho do filtro de listagem (o painel do SIN agrupa assim). Não
 * substitui {@link Tenant.Status}: {@code status} exato ainda existe
 * na query. Se os dois vierem, {@code status} ganha.
 */
public enum SituacaoParoquia {
    ATIVOS(Tenant.Status.ATIVO, Tenant.Status.TRIAL),
    INADIMPLENTES(Tenant.Status.BLOQUEADO),
    INATIVOS(Tenant.Status.CANCELADO);

    private final List<Tenant.Status> status;

    SituacaoParoquia(Tenant.Status... status) {
        this.status = List.of(status);
    }

    public List<Tenant.Status> status() {
        return status;
    }
}
