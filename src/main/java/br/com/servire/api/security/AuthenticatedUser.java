package br.com.servire.api.security;

import br.com.servire.api.auth.UsuarioTenant;

import java.util.UUID;

/**
 * Principal autenticado da requisição atual — montado por
 * {@link JwtAuthenticationFilter} a partir do token já validado
 * (assinatura, expiração, finalidade) e revalidado no banco.
 *
 * <p>Duas formas:</p>
 * <ul>
 *   <li>Usuário da paróquia — {@code tenantId} + {@code role} do
 *   {@code usuario_tenant}; {@code suporte=false}.</li>
 *   <li>Operador da Central em suporte — {@code tenantId} da paróquia,
 *   {@code suporte=true}, {@code usuarioId} = id do código de suporte
 *   (JWT {@code purpose=suporte_app}). Sem vínculo {@code usuario_tenant};
 *   o filtro aceita paróquia bloqueada.</li>
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

    public static AuthenticatedUser suporte(UUID usuarioId, UUID tenantId) {
        return new AuthenticatedUser(usuarioId, tenantId, UsuarioTenant.Role.ADMIN, true);
    }
}
