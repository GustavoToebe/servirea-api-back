package br.com.servire.api.backoffice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Seed opcional do operador em dev ({@code servire.backoffice.operador-email}
 * / {@code operador-senha}). Sem valor padrão de propósito — em
 * {@code application-dev.yml} NÃO colocar {@code ${X:}} vazio (sobrescreve
 * o arquivo local gitignorado). Testes criam o operador na hora.
 */
@ConfigurationProperties(prefix = "servire.backoffice")
public record BackofficeProperties(String operadorEmail, String operadorSenha) {
}
