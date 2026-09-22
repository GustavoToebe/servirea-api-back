package br.com.servire.api.voluntario;

/**
 * Espelha o ENUM nativo do Postgres {@code public.funcao_escala} (V001).
 * Usado hoje só em {@code voluntarios.funcoes_habilitadas} (Fase 6,
 * seção 37) — a partir da Fase 9 (escalas, seção 109) também será usado
 * em {@code escala_vagas.funcao}, quando esse módulo for implementado.
 * Mantido neste pacote por enquanto; se isso criar um acoplamento
 * estranho entre {@code voluntario} e o futuro pacote {@code escala},
 * mover para um pacote compartilhado nessa fase.
 */
public enum FuncaoEscala {
    MISSAL,
    CRUZ,
    CREDENCIA,
    VELA,
    COLETA,
    SINO,
    OUTRO
}
