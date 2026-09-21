-- V018__table_usuario_tenant.sql
-- Fase 3 (seção 103/126, seção 29): vínculo N:N entre usuario e tenant,
-- com role/status. Base do Kill Switch (seção 28) e da troca de paróquia
-- ativa (seção 30) - um usuário pode gerenciar múltiplas paróquias já no
-- MVP (confirmado pelo usuário em 21/09/2026, ver seção 131.1).

CREATE TYPE public.usuario_tenant_role AS ENUM ('ADMIN', 'COORDENADOR', 'VISUALIZADOR');
CREATE TYPE public.usuario_tenant_status AS ENUM ('ATIVO', 'INATIVO');

CREATE TABLE public.usuario_tenant (
    usuario_id   uuid NOT NULL REFERENCES public.usuario(id) ON DELETE CASCADE,
    tenant_id    uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    role         public.usuario_tenant_role NOT NULL DEFAULT 'VISUALIZADOR',
    status       public.usuario_tenant_status NOT NULL DEFAULT 'ATIVO',
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT usuario_tenant_pkey PRIMARY KEY (usuario_id, tenant_id)
);

COMMENT ON TABLE public.usuario_tenant IS
    'Vínculo N:N usuário<->paróquia (seção 29 do plano mestre). A chave primária composta já garante o UNIQUE (usuario_id, tenant_id) pedido na seção 29. Um usuário sem linha status=ATIVO aqui para um tenant não deve conseguir acessar aquele tenant (Kill Switch, seção 28).';

CREATE INDEX idx_usuario_tenant_tenant_id ON public.usuario_tenant (tenant_id);

CREATE TRIGGER trg_usuario_tenant_updated_at
    BEFORE UPDATE ON public.usuario_tenant
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();
