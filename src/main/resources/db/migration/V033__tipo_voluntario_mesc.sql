-- flyway:executeInTransaction=false
-- Ministro extraordinário da Sagrada Comunhão. Migration só do valor
-- novo: no Postgres o valor de enum só pode ser usado depois do commit.

ALTER TYPE public.tipo_voluntario ADD VALUE IF NOT EXISTS 'MESC';
