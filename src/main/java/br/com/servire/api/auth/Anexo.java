package br.com.servire.api.auth;

/** Arquivo anexado a um e-mail de comunicado (PLANO-005). */
public record Anexo(String nome, String tipo, byte[] conteudo) {
}
