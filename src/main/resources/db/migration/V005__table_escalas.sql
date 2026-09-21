-- V005__table_escalas.sql
-- Fonte: seção 9.2 + seção 20.5 (FK created_by -> auth.users ON DELETE SET NULL,
-- confirmada ao vivo).
--
-- DEPENDÊNCIA EXTERNA: auth.users é gerenciada pelo Supabase Auth, não por
-- esta migration (não existe em um PostgreSQL "limpo" fora do Supabase).
-- Para validar este baseline em ambiente local/CI, ver
-- local-dev/00-supabase-stubs.sql, que cria um stub mínimo de auth.users
-- só para permitir a FK e os testes estruturais.

CREATE TABLE public.escalas (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    titulo       text NOT NULL,
    tipo         public.tipo_escala NOT NULL,
    ano          int CHECK (ano BETWEEN 2020 AND 2100),
    mes          int CHECK (mes BETWEEN 1 AND 12),
    status       public.status_escala NOT NULL DEFAULT 'RASCUNHO',
    observacao   text,
    created_by   uuid REFERENCES auth.users(id) ON DELETE SET NULL DEFAULT auth.uid(),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_escalas_ano_mes ON public.escalas (ano, mes);
CREATE INDEX idx_escalas_status ON public.escalas (status);

CREATE TRIGGER trg_escalas_updated_at
    BEFORE UPDATE ON public.escalas
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();
