package br.com.servire.api.auth.dto;

import java.util.List;

/**
 * Resposta de {@code POST /auth/login} — dois formatos possíveis (seção
 * 30): se o usuário tem só uma paróquia vinculada, {@code accessToken}/
 * {@code tenantAtual} já vêm prontos (o refresh token vai num cookie
 * HttpOnly, nunca aqui). Se tem mais de uma, {@code accessToken} vem nulo
 * e {@code tokenSelecaoTenant}/{@code tenantsDisponiveis} são preenchidos
 * — o cliente deve chamar {@code POST /auth/select-tenant} em seguida.
 */
public record LoginResponse(
        boolean precisaSelecionarTenant,
        String accessToken,
        Long expiresInSeconds,
        TenantResumo tenantAtual,
        String tokenSelecaoTenant,
        List<TenantResumo> tenantsDisponiveis) {

    public static LoginResponse completo(String accessToken, long expiresInSeconds, TenantResumo tenantAtual) {
        return new LoginResponse(false, accessToken, expiresInSeconds, tenantAtual, null, null);
    }

    public static LoginResponse pendenteSelecao(String tokenSelecaoTenant, List<TenantResumo> tenantsDisponiveis) {
        return new LoginResponse(true, null, null, null, tokenSelecaoTenant, tenantsDisponiveis);
    }
}
