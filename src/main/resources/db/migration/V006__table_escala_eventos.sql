-- V006__table_escala_eventos.sql
-- Fonte: seção 9.2 + seção 20.5 (FK escala_id -> escalas ON DELETE CASCADE,
-- confirmada ao vivo — igual ao schema.sql local previa).

CREATE TABLE public.escala_eventos (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    escala_id    uuid NOT NULL REFERENCES public.escalas(id) ON DELETE CASCADE,
    data         date NOT NULL,
    horario      time NOT NULL,
    celebracao   text NOT NULL DEFAULT 'Missa',
    created_at   timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.escala_eventos IS
    'Um evento (missa) dentro de uma escala. O front-end atual apaga e recria todos os eventos de uma escala a cada save (seção 46/16.8 do plano mestre) — comportamento preservado nesta reconstrução, não corrigido aqui.';
