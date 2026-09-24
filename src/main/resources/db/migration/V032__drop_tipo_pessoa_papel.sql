-- V032__drop_tipo_pessoa_papel.sql
-- A V031 trocou a coluna pessoa.papel por e_voluntario/e_responsavel e
-- deixou o tipo enum órfão. Nada mais o referencia.

DROP TYPE IF EXISTS public.pessoa_papel;
