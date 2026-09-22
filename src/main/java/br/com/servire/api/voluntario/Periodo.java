package br.com.servire.api.voluntario;

/**
 * Período do dia usado por {@link DisponibilidadeVoluntario} (Fase 11,
 * seção 131.5 item 12 do plano mestre). Deliberadamente uma enum própria,
 * separada de {@link Voluntario.HorarioEstudo} — mesmos três valores, mas
 * domínios conceituais diferentes (horário de estudo é um dado cadastral
 * do voluntário; período de disponibilidade é sobre quando ele PODE
 * servir), então não reaproveitar evita acoplar os dois no futuro se um
 * dia um dos dois precisar de um valor que o outro não tem.
 */
public enum Periodo {
    MANHA,
    TARDE,
    NOITE
}
