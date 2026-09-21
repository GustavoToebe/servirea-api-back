-- V002__function_set_updated_at.sql
--
-- NOTA DE PROVENIÊNCIA (importante): ao contrário das RPCs de negócio
-- (criar_inscricao_impl / aprovar_inscricao_impl / rejeitar_inscricao_impl,
-- ver V011), o corpo exato desta(s) função(ões) de trigger NÃO foi extraído
-- literalmente do painel do Supabase — não fazia parte do levantamento da
-- seção 20 do documento técnico. O comportamento abaixo é RECONSTRUÍDO por
-- inferência a partir do nome (`set_updated_at`) e do padrão observado nas
-- colunas `updated_at` das tabelas (seção 9.2). Antes de tratar este arquivo
-- como definitivo, vale confirmar ao vivo no painel (Database > Functions)
-- se o corpo real bate com o reconstruído aqui.
--
-- Por que existem DUAS funções com o mesmo nome em schemas diferentes:
-- a seção 20.4 (grants) lista tanto `public.set_updated_at` (grants para
-- anon, authenticated) quanto `private.set_updated_at` (grants para anon,
-- authenticated, service_role). Inferência: `public.set_updated_at` é a
-- função original do `schema.sql` versionado (usada pelos triggers de
-- `voluntarios` e `escalas`, que já existiam antes da introdução do schema
-- `private`); `private.set_updated_at` foi criada depois, seguindo o mesmo
-- padrão SECURITY DEFINER + search_path vazio das demais funções de
-- `private` (ver V011), provavelmente para uso nos triggers de `inscricoes`
-- e `inscricao_responsaveis`. Essa segunda parte (qual função é usada em
-- qual trigger) também é inferência, não fato confirmado.

CREATE SCHEMA IF NOT EXISTS private;

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION private.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$function$;
