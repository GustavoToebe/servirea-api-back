package br.com.servire.api.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP do cliente para rate limit e auditoria.
 *
 * <p>Não lê {@code X-Forwarded-For} no código — um cliente forjaria o
 * header e esvaziaria o limitador. O Tomcat {@code RemoteIpValve}
 * ({@code server.forward-headers-strategy: native} no profile
 * {@code prod}) só reescreve {@link HttpServletRequest#getRemoteAddr()}
 * quando o hop imediato está em {@code server.tomcat.remoteip.internal-proxies}.
 * Fora disso, o valor é o IP TCP real.</p>
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String de(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
