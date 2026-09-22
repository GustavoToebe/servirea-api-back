package br.com.servire.api.escala;

/**
 * Controle de faltas (Fase 11 do plano mestre — item 11 da seção 122,
 * detalhado na seção 131.5). Mapeia o tipo nativo {@code presenca_vaga}
 * do Postgres (V024), mesmo padrão simples já confirmado em
 * {@link br.com.servire.api.voluntario.Voluntario#getTipo()}/
 * {@code Tenant.status} — coluna de enum único, não array.
 */
public enum Presenca {
    /** Valor inicial — evento ainda não ocorreu, ou presença ainda não foi conferida pelo coordenador. */
    PENDENTE,
    PRESENTE,
    FALTOU
}
