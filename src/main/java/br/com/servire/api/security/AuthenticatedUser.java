package br.com.servire.api.security;

import br.com.servire.api.auth.UsuarioTenant;

import java.util.UUID;

/**
 * Principal autenticado da requisição atual — montado por
 * {@link JwtAuthenticationFilter} a partir do token já validado
 * (assinatura, expiração, finalidade) e revalidado no banco.
 *
 * <p>Três formas, combinadas nesta fase (backoffice, seção 111):</p>
 * <ul>
 *   <li>Padre/coordenador — {@code tenantId} + {@code role} do
 *   {@code usuario_tenant}; {@code suporte=false}.</li>
 *   <li>Operador no painel — {@code tenantId} e {@code role} nulos
 *   (JWT {@code purpose=backoffice}); autoridade só
 *   {@code PERM_BACKOFFICE}.</li>
 *   <li>Operador em suporte — {@code tenantId} da paróquia escolhida,
 *   {@code role=ADMIN}, {@code suporte=true} (claim no access token).
 *   Sem vínculo {@code usuario_tenant}; o filtro aceita tenant
 *   {@code BLOQUEADO}.</li>
 * </ul>
 *
 * <p>Fica disponível em {@code SecurityContextHolder} como o
 * {@code principal} da {@code Authentication} — em controllers pode ser
 * obtido via {@code @AuthenticationPrincipal AuthenticatedUser usuario}.
 * O front da paróquia usa {@code suporte} para mostrar a faixa de
 * "modo suporte" (tela em outro repositório).</p>
 */
public record AuthenticatedUser(UUID usuarioId, UUID tenantId, UsuarioTenant.Role role, boolean suporte) {

    /** Construtor do login da paróquia (sem suporte). */
    public AuthenticatedUser(UUID usuarioId, UUID tenantId, UsuarioTenant.Role role) {
        this(usuarioId, tenantId, role, false);
    }

    public static AuthenticatedUser backoffice(UUID usuarioId) {
        return new AuthenticatedUser(usuarioId, null, null, false);
    }

    public static AuthenticatedUser suporte(UUID usuarioId, UUID tenantId) {
        return new AuthenticatedUser(usuarioId, tenantId, UsuarioTenant.Role.ADMIN, true);
    }

    public boolean isBackoffice() {
        return tenantId == null && !suporte;
    }
}
