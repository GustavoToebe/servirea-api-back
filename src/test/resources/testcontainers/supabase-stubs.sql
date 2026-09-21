-- 00-supabase-stubs.sql
--
-- ISTO NÃO É UMA MIGRATION FLYWAY. Não faz parte do schema versionado do
-- Servire nem seria aplicado em produção (lá, auth.*, storage.* e os roles
-- anon/authenticated/service_role já existem, geridos pelo Supabase).
--
-- Serve só para permitir validar as migrations V001-V015 em um PostgreSQL
-- "limpo" local (ou futuramente em Testcontainers, seção 77 do plano
-- mestre), reproduzindo o mínimo necessário de:
--   - roles anon / authenticated / service_role (usados em GRANT e RLS);
--   - schema auth com uma tabela users e uma função uid() (usados em FKs
--     e nas RPCs de aprovação/rejeição);
--   - schema storage com buckets/objects (usado pela migration de bucket).
--
-- Não tenta reproduzir o comportamento completo do Supabase Auth/Storage,
-- só a forma estrutural mínima para as migrations rodarem sem erro.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        CREATE ROLE anon NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        CREATE ROLE authenticated NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        CREATE ROLE service_role NOLOGIN BYPASSRLS;
    END IF;
END
$$;

CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.users (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email      text,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Em produção, auth.uid() lê o claim "sub" do JWT da requisição atual
-- (via current_setting('request.jwt.claims', true)). Aqui, um stub simples
-- que lê uma GUC de sessão — suficiente para exercitar o código das RPCs
-- em teste manual (SET LOCAL servire_test.current_user_id = '<uuid>';).
CREATE OR REPLACE FUNCTION auth.uid()
RETURNS uuid
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('servire_test.current_user_id', true), '')::uuid;
$$;

CREATE SCHEMA IF NOT EXISTS storage;

CREATE TABLE IF NOT EXISTS storage.buckets (
    id                 text PRIMARY KEY,
    name               text NOT NULL,
    public             boolean NOT NULL DEFAULT false,
    file_size_limit    bigint,
    allowed_mime_types text[],
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS storage.objects (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket_id  text REFERENCES storage.buckets(id),
    name       text,
    owner      uuid,
    metadata   jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;

GRANT USAGE ON SCHEMA auth TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA storage TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;

-- Nota: o schema `private` só é criado pela migration V002 (roda depois
-- deste stub) — por isso o GRANT USAGE nele não entra aqui. Se for preciso
-- testar chamadas de RPC como o role `authenticated`/`service_role` de
-- verdade (não só como superuser local), rode manualmente depois das
-- migrations:
--   GRANT USAGE ON SCHEMA private TO anon, authenticated, service_role;
