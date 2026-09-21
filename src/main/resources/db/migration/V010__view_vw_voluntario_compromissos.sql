-- V010__view_vw_voluntario_compromissos.sql
-- Fonte: seção 9.3 do documento técnico (copiada literalmente do
-- supabase/schema.sql do repositório do front-end).
--
-- Dívida técnica preservada e já registrada no plano mestre (seção 16 item
-- 33): esta view NÃO filtra escalas CANCELADAS/RASCUNHO, então o perfil do
-- voluntário mostra compromissos de escalas que talvez nem aconteçam mais.
-- Não corrigido aqui — reconstrução fiel do estado atual, não uma correção.

CREATE VIEW public.vw_voluntario_compromissos
WITH (security_invoker = true) AS
SELECT
    v.voluntario_id,
    s.id AS escala_id,
    s.titulo AS escala_titulo,
    s.status::text AS escala_status,
    e.data,
    e.horario,
    e.celebracao,
    v.funcao
FROM public.escala_vagas v
JOIN public.escala_eventos e ON e.id = v.evento_id
JOIN public.escalas s ON s.id = e.escala_id
WHERE v.voluntario_id IS NOT NULL;
