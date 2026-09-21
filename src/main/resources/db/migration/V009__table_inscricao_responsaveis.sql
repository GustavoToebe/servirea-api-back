-- V009__table_inscricao_responsaveis.sql
-- Fonte: seção 9.2 + seção 20.5 (FK inscricao_id -> inscricoes ON DELETE
-- CASCADE e índice único parcial ux_inscricao_responsavel_principal,
-- confirmados ao vivo). Também ausente do schema.sql versionado.

CREATE TABLE public.inscricao_responsaveis (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    inscricao_id   uuid NOT NULL REFERENCES public.inscricoes(id) ON DELETE CASCADE,
    parentesco     text NOT NULL,
    nome           text NOT NULL,
    telefone       text,
    celular        text,
    email          text,
    principal      boolean NOT NULL DEFAULT false,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_inscricao_responsavel_principal
    ON public.inscricao_responsaveis (inscricao_id)
    WHERE principal = true;

CREATE TRIGGER trg_inscricao_responsaveis_updated_at
    BEFORE UPDATE ON public.inscricao_responsaveis
    FOR EACH ROW
    EXECUTE FUNCTION private.set_updated_at();
