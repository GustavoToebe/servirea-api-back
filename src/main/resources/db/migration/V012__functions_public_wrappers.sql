-- V012__functions_public_wrappers.sql
-- Fonte: seção 20.3 do documento técnico descreve o comportamento
-- ("os wrappers públicos só fazem SELECT private.xxx_impl(...) — não têm
-- lógica própria"), mas NÃO cola o corpo literal destes três wrappers
-- (só das implementações em `private`, ver V011). O corpo abaixo é
-- RECONSTRUÍDO seguindo exatamente essa descrição — vale confirmar ao vivo
-- (Database > Functions > schema public) antes de tratar como definitivo,
-- em especial a linguagem exata (aqui assumida como `sql`, por ser a forma
-- mais direta de implementar "só um SELECT delegando para outra função").

CREATE OR REPLACE FUNCTION public.criar_inscricao_publica(p_dados jsonb, p_responsaveis jsonb)
RETURNS uuid
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $function$
    SELECT private.criar_inscricao_impl(p_dados, p_responsaveis);
$function$;

CREATE OR REPLACE FUNCTION public.aprovar_inscricao(p_inscricao_id uuid)
RETURNS uuid
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $function$
    SELECT private.aprovar_inscricao_impl(p_inscricao_id);
$function$;

CREATE OR REPLACE FUNCTION public.rejeitar_inscricao(p_inscricao_id uuid, p_motivo text DEFAULT NULL::text)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $function$
    SELECT private.rejeitar_inscricao_impl(p_inscricao_id, p_motivo);
$function$;
