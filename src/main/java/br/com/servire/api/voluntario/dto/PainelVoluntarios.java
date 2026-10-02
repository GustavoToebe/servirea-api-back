package br.com.servire.api.voluntario.dto;

/**
 * Composição dos voluntários ativos para o início, calculada no banco (T05). Substitui o carregamento da lista inteira só
 * para contar: {@code coroinhas} e {@code acolitos} incluem quem é {@code AMBOS}; {@code mandatosAVencer} inclui os já vencidos.
 */
public record PainelVoluntarios(long ativos, long coroinhas, long acolitos, long mesc, long mandatosAVencer) {
}
