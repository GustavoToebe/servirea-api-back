-- V007__table_escala_vagas.sql
-- Fonte: seção 9.2 + seção 20.5 (FKs, UNIQUE (evento_id, funcao, posicao) e
-- índice único parcial uq_voluntario_por_evento, todos confirmados ao vivo
-- em produção).

CREATE TABLE public.escala_vagas (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    evento_id      uuid NOT NULL REFERENCES public.escala_eventos(id) ON DELETE CASCADE,
    funcao         public.funcao_escala NOT NULL,
    posicao        int NOT NULL DEFAULT 1 CHECK (posicao > 0),
    voluntario_id  uuid REFERENCES public.voluntarios(id) ON DELETE SET NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT escala_vagas_evento_id_funcao_posicao_key
        UNIQUE (evento_id, funcao, posicao)
);

-- Um voluntário não pode ocupar duas vagas na mesma missa (mesmo evento).
CREATE UNIQUE INDEX uq_voluntario_por_evento
    ON public.escala_vagas (evento_id, voluntario_id)
    WHERE voluntario_id IS NOT NULL;
