-- V001__enums.sql
-- Baseline (Fase 0/1 do plano mestre): reconstrução do schema real de produção
-- do projeto Supabase "qcybebkhwhrudbwoweip" (Paróquia São José Operário),
-- conforme levantado ao vivo em 21/09/2026 e documentado em
-- claude/DOCUMENTACAO-COMPLETA-PARA-IA.md, seções 9, 16 e 20.
--
-- Este arquivo reproduz os ENUMs confirmados em produção (seção 9.1 do
-- documento técnico: schema.sql local tinha 4 ENUMs; produção tem um quinto,
-- status_inscricao, ausente do schema.sql versionado).

CREATE TYPE public.tipo_voluntario AS ENUM (
    'COROINHA',
    'ACOLITO',
    'AMBOS'
);

CREATE TYPE public.funcao_escala AS ENUM (
    'MISSAL',
    'CRUZ',
    'CREDENCIA',
    'VELA',
    'COLETA',
    'SINO',
    'OUTRO'
);

CREATE TYPE public.tipo_escala AS ENUM (
    'SEMANAL',
    'MENSAL'
);

CREATE TYPE public.status_escala AS ENUM (
    'RASCUNHO',
    'FINALIZADA',
    'CANCELADA'
);

CREATE TYPE public.status_inscricao AS ENUM (
    'PENDENTE',
    'APROVADA',
    'REJEITADA'
);
