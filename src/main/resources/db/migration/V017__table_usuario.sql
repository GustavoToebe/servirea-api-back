-- V017__table_usuario.sql
-- Fase 3 (seção 103/126): usuário global (Master lógico, seção 25). Ainda
-- SEM autenticação própria funcionando (isso é Fase 5, seção 105/32-36) -
-- esta migration só cria a tabela para não ter que redesenhar o schema
-- depois ("Isso evita refazer autenticação depois", seção 103).

CREATE TABLE public.usuario (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email         text NOT NULL,
    senha_hash    text,
    nome          text NOT NULL,
    ativo         boolean NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT usuario_email_key UNIQUE (email)
);

COMMENT ON TABLE public.usuario IS
    'Usuário global do SaaS (Master lógico, seção 25) - pode estar vinculado a mais de uma paróquia via usuario_tenant (seção 29).';
COMMENT ON COLUMN public.usuario.senha_hash IS
    'Hash Argon2 ou BCrypt (seção 32 do plano mestre) - nunca senha reversível. Nullable por enquanto: a Fase 5 (autenticação própria) é quem efetivamente popula/usa esta coluna; até lá a tabela existe só como parte do modelo.';

CREATE TRIGGER trg_usuario_updated_at
    BEFORE UPDATE ON public.usuario
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();
