-- V049: descrição e "layout padrão" do layout de escala (editor visual de layouts).
-- `ativo` já existia (V046): layout inativo continua na lista de layouts, mas não é oferecido na montagem da escala.
-- `padrao`: no máximo um layout padrão por paróquia e modelo (semanal/mensal). É o que a escala nova usa
-- quando ninguém escolhe layout; sem nenhum marcado, vale o layout de sistema ativo.

ALTER TABLE public.layout_escala ADD COLUMN descricao text;
ALTER TABLE public.layout_escala ADD COLUMN padrao boolean NOT NULL DEFAULT false;

-- Quem já era o padrão de fato (o layout de sistema ativo mais antigo de cada paróquia e modelo) passa a estar marcado.
UPDATE public.layout_escala
   SET padrao = true
 WHERE id IN (SELECT DISTINCT ON (tenant_id, tipo) id
                FROM public.layout_escala
               WHERE sistema AND ativo
               ORDER BY tenant_id, tipo, created_at, id);

CREATE UNIQUE INDEX uq_layout_escala_padrao_tenant_tipo ON public.layout_escala (tenant_id, tipo) WHERE padrao;
