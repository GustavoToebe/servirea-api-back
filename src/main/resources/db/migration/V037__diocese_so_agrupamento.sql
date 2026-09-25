-- Diocese passa a ser só agrupamento informativo da paróquia (decisão de
-- 25/09/2026). Sai a cota de servidores somada entre paróquias da V034:
-- o gatilho que mantinha o espelho, o espelho em tenant e a coluna da cota.
-- Se um dia uma diocese contratar em bloco, ela vira cliente na Central,
-- não regra do Servire.

DROP TRIGGER IF EXISTS trg_voluntarios_ativos ON public.voluntarios;
DROP FUNCTION IF EXISTS public.recalcular_voluntarios_ativos();

ALTER TABLE public.tenant DROP COLUMN IF EXISTS voluntarios_ativos;
ALTER TABLE public.diocese DROP COLUMN IF EXISTS cota_voluntarios;

-- A paróquia digita o nome; "Diocese de Cascavel" e "diocese de cascavel"
-- são a mesma diocese.
ALTER TABLE public.diocese DROP CONSTRAINT IF EXISTS diocese_nome_key;
CREATE UNIQUE INDEX diocese_nome_lower_key ON public.diocese (lower(nome));

COMMENT ON TABLE public.diocese IS
    'Diocese que agrupa paróquias. Só informativa: sem cota nem regra de acesso.';
