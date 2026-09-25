package br.com.servire.api.voluntario;

/**
 * Espelha o ENUM nativo do Postgres {@code public.tipo_voluntario} (V001,
 * seção 37 do plano mestre). Os nomes das constantes têm que bater
 * exatamente com os valores do ENUM do banco — é isso que
 * {@code @JdbcTypeCode(SqlTypes.NAMED_ENUM)} (ver {@link Voluntario})
 * exige para mapear direto no tipo nativo, sem conversão manual.
 */
public enum TipoVoluntario {
    COROINHA,
    ACOLITO,
    AMBOS,
    /** Ministro extraordinário da Sagrada Comunhão. */
    MESC
}
