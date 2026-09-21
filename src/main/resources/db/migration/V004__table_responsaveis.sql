-- V004__table_responsaveis.sql
-- Fonte: seção 9.2 (dump) + seção 20.5 (FK ON DELETE CASCADE e unique
-- parcial confirmados ao vivo em produção — o dump colado originalmente
-- pelo usuário não mostrava o ON DELETE, mas a produção real já tinha
-- CASCADE, igual ao schema.sql local previa).

CREATE TABLE public.responsaveis (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    voluntario_id  uuid NOT NULL REFERENCES public.voluntarios(id) ON DELETE CASCADE,
    parentesco     text NOT NULL,
    nome           text NOT NULL,
    telefone       text,
    celular        text,
    email          text,
    principal      boolean NOT NULL DEFAULT false,
    created_at     timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.responsaveis IS
    'Responsáveis legais de um voluntário já aprovado. Deve existir exatamente um principal=true (seção 38 do plano mestre) — reforçado pelo índice único parcial abaixo.';

CREATE UNIQUE INDEX uq_responsavel_principal_por_voluntario
    ON public.responsaveis (voluntario_id)
    WHERE principal = true;
