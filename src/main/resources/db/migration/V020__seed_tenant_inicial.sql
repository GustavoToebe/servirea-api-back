-- V020__seed_tenant_inicial.sql
-- Fase 3/4 (seção 103/104/126): cria o tenant inicial ao qual todos os
-- dados de domínio já existentes (das migrations V003-V009) serão
-- atribuídos no backfill da V021 (seção 114: "todos os dados existentes
-- deverão ser atribuídos ao tenant inicial correto"). UUID fixo e
-- conhecido (não aleatório) de propósito, para poder ser referenciado por
-- código sem precisar consultar o banco antes (ex.: tenant fixo em
-- desenvolvimento, seção 104 - "Inicialmente tenant pode ser fixo em
-- desenvolvimento. Depois passa a vir do JWT").
--
-- ATENÇÃO: codigo/slug/nome abaixo são PLACEHOLDER. Não inventei o nome
-- real da paróquia porque isso não foi informado nesta sessão. Antes de
-- usar este ambiente além de desenvolvimento/teste local, atualizar:
--
--   UPDATE public.tenant
--   SET codigo = '...', slug = '...', nome = '...',
--       razao_social = '...', cnpj = '...'
--   WHERE id = '00000000-0000-0000-0000-000000000001';

INSERT INTO public.tenant (id, codigo, slug, nome, status)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'PLACEHOLDER',
    'placeholder',
    'Paróquia inicial (renomear - ver comentário desta migration)',
    'ATIVO'
);
