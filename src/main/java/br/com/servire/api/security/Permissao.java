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
 * ({@link br.com.servire.api.tenant.TenantController}). {@code BACKOFFICE}
 * protege {@code /admin/**} e só sai do JWT {@code purpose=backoffice}
 * — nunca das roles da paróquia (seção 111).</p>
 */
public enum Permissao {
    VOLUNTARIO_READ,
    VOLUNTARIO_WRITE,
    ESCALA_READ,
    ESCALA_WRITE,
    INSCRICAO_READ,
    INSCRICAO_APPROVE,
    CONFIG_WRITE,
    /**
     * Painel do operador do SaaS ({@code /admin/**}, seção 111). NÃO entra
     * no mapeamento das roles da paróquia — um ADMIN da paróquia não
     * acessa o backoffice. Concedida só pelo JWT {@code purpose=backoffice}.
     * O backoffice saiu do Servire na etapa 1 da Central; o valor fica
     * para um token antigo não virar permissão de paróquia por acidente.
     */
    BACKOFFICE,
    PERFIL,
    PERFIL_CRIAR,
    PERFIL_ALTERAR,
    USUARIO,
    USUARIO_CRIAR,
    USUARIO_ALTERAR,
    USUARIO_REENVIAR_CONVITE,
    PAROQUIA,
    PAROQUIA_ALTERAR,
    PESSOA,
    PESSOA_CRIAR,
    PESSOA_ALTERAR,
    PESSOA_EXCLUIR,
    PESSOA_ATIVAR_INATIVAR,
    ESCALA,
    ESCALA_CRIAR,
    ESCALA_ALTERAR,
    ESCALA_EXCLUIR,
    ESCALA_FINALIZAR_REABRIR,
    ESCALA_CANCELAR,
    VAGA,
    VAGA_ALOCAR,
    VAGA_PRESENCA,
    INSCRICAO,
    INSCRICAO_ALTERAR,
    INSCRICAO_APROVAR,
    INSCRICAO_REJEITAR,
    AUDITORIA
}
