-- V013__grants.sql
-- Fonte: extraído ao vivo do painel do Supabase em 21/09/2026 (documento
-- técnico, seção 20.4 — "quem pode executar cada função"). Reproduzido
-- literalmente: este é o ponto mais crítico de segurança do baseline
-- (garante que `criar_inscricao_impl`/`criar_inscricao_publica` só rodam
-- com service_role, nunca com anon/authenticated).
--
-- DEPENDÊNCIA EXTERNA: os roles anon / authenticated / service_role são
-- criados automaticamente por qualquer projeto Supabase. Em um PostgreSQL
-- "limpo" (local/CI), eles precisam ser criados manualmente antes de rodar
-- esta migration — ver local-dev/00-supabase-stubs.sql.

-- Por padrão, revogar de PUBLIC antes de conceder explicitamente (defesa em
-- profundidade — mesmo que o Supabase já não conceda a PUBLIC por padrão).
REVOKE ALL ON FUNCTION private.criar_inscricao_impl(jsonb, jsonb) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.criar_inscricao_publica(jsonb, jsonb) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION private.aprovar_inscricao_impl(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION private.criar_inscricao_impl(jsonb, jsonb) TO service_role;
GRANT EXECUTE ON FUNCTION private.rejeitar_inscricao_impl(uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION private.set_updated_at() TO anon, authenticated, service_role;

GRANT EXECUTE ON FUNCTION public.aprovar_inscricao(uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.criar_inscricao_publica(jsonb, jsonb) TO service_role;
GRANT EXECUTE ON FUNCTION public.rejeitar_inscricao(uuid, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.set_updated_at() TO anon, authenticated;
