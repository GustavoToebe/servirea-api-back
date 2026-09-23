-- V028__filtro_paroquia.sql
-- Filtros da listagem de paróquias no backoffice (seção 111): "quando
-- vai acabar" precisa de uma data de vigência; "tipo de e-mail" é o
-- rótulo do único e-mail de contato da paróquia (CONTATO / FINANCEIRO /
-- ADMINISTRATIVO) — não há lista de e-mails como no SIN.
-- created_at (já existia) cobre "quando contratou".

ALTER TABLE public.tenant
    ADD COLUMN vigencia_ate date,
    ADD COLUMN tipo_email text;

COMMENT ON COLUMN public.tenant.vigencia_ate IS
    'Fim da vigência (trial de 7 dias na criação, seção 131.3; depois o operador ajusta / marcar pago). Usado no filtro "quando vai acabar".';
COMMENT ON COLUMN public.tenant.tipo_email IS
    'Classificação do e-mail de contato (texto livre; o front pode oferecer CONTATO/FINANCEIRO/ADMINISTRATIVO).';
