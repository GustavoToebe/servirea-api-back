package br.com.servire.api.security;

/**
 * Permissões conceituais (seção 31 do plano mestre) — "Preferir permissões
 * conceituais" a espalhar checagens rígidas de {@code role} por todo o
 * sistema. O mapeamento {@code role -> permissions} concreto fica em
 * {@link RolePermissoes}; endpoints (controllers) checam só a permissão,
 * nunca a role diretamente (via {@code @PreAuthorize("hasAuthority(...)")})
 * — isso é o que permite adicionar uma role nova no futuro (SECRETARIA,
 * SUPORTE etc., já cogitadas na seção 31) sem tocar em nenhum controller.
 *
 * <p>{@code CONFIG_WRITE} protege {@code GET}/{@code PUT /tenant}
 * ({@link br.com.servire.api.tenant.TenantController}).</p>
 */
public enum Permissao {
    VOLUNTARIO_READ,
    VOLUNTARIO_WRITE,
    ESCALA_READ,
    ESCALA_WRITE,
    INSCRICAO_READ,
    INSCRICAO_APPROVE,
    CONFIG_WRITE
}
