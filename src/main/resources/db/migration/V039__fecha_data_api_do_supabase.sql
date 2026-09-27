-- V039: fecha o schema public para a Data API do Supabase (27/09/2026).
--
-- Por quê: o projeto novo do Supabase (sa-east-1) nasce só pelo Flyway. Nele,
-- 9 tabelas ficavam sem RLS (usuario, refresh_token, password_reset_token,
-- tenant...) e as policies "authenticated ALL" da V014, do tempo em que o
-- front falava direto com o Supabase, continuavam valendo: com a chave
-- publicável do front e uma conta criada no Supabase Auth, dava para ler e
-- alterar voluntários de todas as paróquias. No banco antigo um script manual
-- ligava o RLS; agora a regra fica versionada.
--
-- Quem usa o banco é só a API Java, conectada como dona das tabelas (RLS não
-- se aplica ao dono). Por isso: RLS em todas as tabelas, nenhuma policy,
-- nenhum grant para anon/authenticated e a view com security_invoker.
-- As funções do schema private (testadas no FlywayMigrationIntegrationTest)
-- não mudam: o schema private não é exposto pela Data API.

DO $$
DECLARE
    t record;
BEGIN
    FOR t IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND NOT c.relrowsecurity
          -- O Flyway segura esta tabela em outra conexão durante a migração (ALTER esperaria para
          -- sempre); ela fica fechada pela falta de grant abaixo.
          AND c.relname <> 'flyway_schema_history'
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.relname);
    END LOOP;

    FOR t IN SELECT tablename, policyname FROM pg_policies WHERE schemaname = 'public'
    LOOP
        EXECUTE format('DROP POLICY %I ON public.%I', t.policyname, t.tablename);
    END LOOP;
END
$$;

ALTER VIEW public.vw_voluntario_compromissos SET (security_invoker = true);

-- RPC pela Data API: as funções public são SECURITY DEFINER legadas (a API
-- Java não as chama) e os gatilhos rodam como dono.
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;

DO $$
DECLARE
    papel text;
BEGIN
    FOREACH papel IN ARRAY ARRAY['anon', 'authenticated']
    LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = papel) THEN
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', papel);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', papel);
            EXECUTE format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM %I', papel);
            -- Tabelas futuras criadas pelo Flyway (este mesmo usuário) já nascem fechadas.
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I', papel);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I', papel);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM %I', papel);
        END IF;
    END LOOP;
END
$$;
