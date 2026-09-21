-- V016__table_tenant.sql
-- Fase 3 do plano mestre (seção 103/126): primeira tabela do "Master
-- lógico" (seção 25) - dado global, sem tenant_id (ela própria é a raiz
-- da hierarquia multi-tenant, seção 17/27).

CREATE TYPE public.tenant_status AS ENUM ('ATIVO', 'TRIAL', 'BLOQUEADO', 'CANCELADO');

CREATE TABLE public.tenant (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo        text NOT NULL,
    slug          text NOT NULL,
    nome          text NOT NULL,
    razao_social  text,
    cnpj          text,
    status        public.tenant_status NOT NULL DEFAULT 'TRIAL',
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT tenant_codigo_key UNIQUE (codigo),
    CONSTRAINT tenant_slug_key UNIQUE (slug)
);

COMMENT ON TABLE public.tenant IS
    'Cada linha é uma paróquia (tenant) do SaaS. Tabela global do "Master lógico" (seção 25 do plano mestre) - não possui tenant_id, ela é a raiz da hierarquia multi-tenant.';
COMMENT ON COLUMN public.tenant.slug IS
    'Identificador público usado em URLs (ex.: /public/paroquia-sao-jose/inscricoes, seção 21 do plano mestre).';
COMMENT ON COLUMN public.tenant.status IS
    'Autoridade central de acesso (Kill Switch, seção 28): ATIVO/TRIAL liberam acesso normal; BLOQUEADO/CANCELADO negam acesso totalmente (decidido em 21/09/2026 - sem modo somente leitura).';

CREATE TRIGGER trg_tenant_updated_at
    BEFORE UPDATE ON public.tenant
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();
