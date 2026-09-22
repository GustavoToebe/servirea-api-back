-- V026__table_audit_log.sql
-- Tabela de auditoria (seção 59 do plano mestre) — tenant-aware, registrando
-- SÓ os nomes dos campos alterados (não uma cópia completa dos dados
-- pessoais antes/depois, seção 59: "Evitar transformar audit_log em cópia
-- completa dos dados pessoais").
--
-- Nota de implementação: a seção 59 sugere changed_fields como um JSON
-- (ex. {"changedFields": ["telefone","endereco"]}); optou-se aqui por um
-- array nativo do Postgres (text[]) em vez de jsonb — guarda exatamente a
-- mesma informação (lista de nomes de campo), com um mapeamento JPA mais
-- simples e de menor risco (String[] -> text[] é direto, sem precisar do
-- combo @JdbcTypeCode(ARRAY) + @Enumerated + @ColumnTransformer que
-- voluntarios.funcoes_habilitadas exigiu, já que aqui os elementos são
-- texto livre, não um ENUM do Postgres).

CREATE TABLE public.audit_log (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES public.tenant(id),
    -- Nullable: ações do formulário público de inscrição (seção 44) não têm
    -- um usuário autenticado por trás.
    user_id         uuid REFERENCES public.usuario(id) ON DELETE SET NULL,
    acao            text NOT NULL,
    entidade        text NOT NULL,
    entidade_id     uuid NOT NULL,
    changed_fields  text[],
    ip              text,
    request_id      text,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_tenant_entidade ON public.audit_log (tenant_id, entidade, entidade_id);
CREATE INDEX idx_audit_log_tenant_created_at ON public.audit_log (tenant_id, created_at DESC);

COMMENT ON TABLE public.audit_log IS
    'Trilha de auditoria (seção 59) — quem fez o quê, em qual entidade, sem duplicar o conteúdo pessoal em si (só os nomes dos campos alterados).';
