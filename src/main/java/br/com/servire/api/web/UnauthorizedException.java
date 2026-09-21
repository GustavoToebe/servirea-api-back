package br.com.servire.api.web;

import org.springframework.http.HttpStatus;

/**
 * Credenciais ausentes, inválidas ou expiradas (login com senha errada,
 * JWT expirado/adulterado, refresh token revogado/reutilizado etc — Fase 5,
 * seção 32/33/36). Distinto de {@link ForbiddenException}: aqui a
 * identidade em si não foi estabelecida; lá a identidade é conhecida mas a
 * permissão é insuficiente.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
