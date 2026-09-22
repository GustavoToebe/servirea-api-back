-- V015__storage_bucket.sql
-- Fonte: seção 9.5 do documento técnico (config do bucket) — extraída do
-- painel, mas de forma descritiva (não há um dump de pg_policies para
-- storage.objects equivalente ao que a seção 20.2 fez para as tabelas de
-- domínio). As políticas de storage.objects abaixo são RECONSTRUÍDAS a
-- partir da descrição "Policies: authenticated SELECT/INSERT/UPDATE/DELETE
-- no bucket" — os nomes de policy e a forma exata do predicado não foram
-- confirmados ao vivo como foram os de pg_policies em 20.2. Recomenda-se
-- validar isso da mesma forma que a seção 20.2 fez para as tabelas antes
-- de tratar esta migration como 100% fiel à produção.
--
-- DEPENDÊNCIA EXTERNA: storage.buckets / storage.objects são gerenciadas
-- pelo Supabase Storage. Ver src/test/resources/testcontainers/supabase-stubs.sql
-- (caminho corrigido em 22/09/2026, ver nota em V005) e o README.md,
-- "Como rodar localmente", para o stub usado na validação estrutural local.

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'voluntarios-fotos',
    'voluntarios-fotos',
    false,
    5242880, -- 5 MB, confirmado na seção 9.5
    ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/heic']
)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY auth_select_voluntarios_fotos ON storage.objects
    FOR SELECT TO authenticated
    USING (bucket_id = 'voluntarios-fotos');

CREATE POLICY auth_insert_voluntarios_fotos ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (bucket_id = 'voluntarios-fotos');

CREATE POLICY auth_update_voluntarios_fotos ON storage.objects
    FOR UPDATE TO authenticated
    USING (bucket_id = 'voluntarios-fotos');

CREATE POLICY auth_delete_voluntarios_fotos ON storage.objects
    FOR DELETE TO authenticated
    USING (bucket_id = 'voluntarios-fotos');
