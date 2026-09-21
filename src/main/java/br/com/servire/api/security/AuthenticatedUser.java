package br.com.servire.api.security;

import br.com.servire.api.auth.UsuarioTenant;

import java.util.UUID;

/**
 * Principal autenticado da requisição atual — montado por
 * {@link JwtAuthenticationFilter} a partir do access token já validado
 * (assinatura, expiração, finalidade) e revalidado no banco (Kill Switch,
 * seção 28: usuário ativo, vínculo ATIVO, tenant ATIVO/TRIAL).
 *
 * <p>Fica disponível em {@code SecurityContextHolder} como o
 * {@code principal} da {@code Authentication} — em controllers futuros
 * pode ser obtido via {@code @AuthenticationPrincipal AuthenticatedUser
 * usuario}, sem precisar decodificar o JWT de novo.</p>
 */
public record AuthenticatedUser(UUID usuarioId, UUID tenantId, UsuarioTenant.Role role) {
}
