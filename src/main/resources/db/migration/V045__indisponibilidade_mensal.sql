-- Indisponibilidade mensal (PLANO-007, 28/09/2026): as datas em que o voluntário NÃO pode, digitadas pela
-- equipe para montar a escala mensal. É negativa e por data; não confundir com disponibilidade_voluntario
-- (V025, positiva). periodo nulo = o dia inteiro. resposta_indisponibilidade separa "respondeu sem
-- restrição" de "não respondeu".

CREATE TABLE public.indisponibilidade_voluntario (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    voluntario_id uuid NOT NULL,
    data          date NOT NULL,
    periodo       public.periodo_dia,
    observacao    varchar(200),
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT indisponibilidade_voluntario_tenant_id_id_key UNIQUE (tenant_id, id),
    CONSTRAINT fk_indisponibilidade_voluntario FOREIGN KEY (tenant_id, voluntario_id)
        REFERENCES public.voluntarios (tenant_id, id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ux_indisponibilidade_dia_inteiro
    ON public.indisponibilidade_voluntario (tenant_id, voluntario_id, data) WHERE periodo IS NULL;
CREATE UNIQUE INDEX ux_indisponibilidade_periodo
    ON public.indisponibilidade_voluntario (tenant_id, voluntario_id, data, periodo) WHERE periodo IS NOT NULL;
CREATE INDEX idx_indisponibilidade_tenant_data ON public.indisponibilidade_voluntario (tenant_id, data);

CREATE TABLE public.resposta_indisponibilidade (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    voluntario_id uuid NOT NULL,
    ano           integer NOT NULL,
    mes           integer NOT NULL CHECK (mes BETWEEN 1 AND 12),
    sem_restricao boolean NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_resposta_voluntario_mes UNIQUE (tenant_id, voluntario_id, ano, mes),
    CONSTRAINT fk_resposta_voluntario FOREIGN KEY (tenant_id, voluntario_id)
        REFERENCES public.voluntarios (tenant_id, id) ON DELETE CASCADE
);

ALTER TABLE public.indisponibilidade_voluntario ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.resposta_indisponibilidade ENABLE ROW LEVEL SECURITY;
