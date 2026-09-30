-- V051: o financeiro e o painel antigo saíram do Servire e foram para a Central.
-- Nenhuma entidade, repositório ou SQL do código lê estas tabelas (conferido em 30/09/2026).
-- O histórico de cobrança, se existia, fica só na Central. Esta migration apaga o que sobrou aqui.
-- Não mexe em tenant.ultimo_pagamento_em: a entidade Tenant ainda mapeia a coluna.

DROP TABLE IF EXISTS public.cobranca;
DROP TABLE IF EXISTS public.assinatura;
DROP TABLE IF EXISTS public.preco_plano;
DROP TABLE IF EXISTS public.plano;
DROP TABLE IF EXISTS public.backoffice_log;

DROP TYPE IF EXISTS public.cobranca_status;
DROP TYPE IF EXISTS public.assinatura_status;
DROP TYPE IF EXISTS public.forma_pagamento;
DROP TYPE IF EXISTS public.plano_periodicidade;

ALTER TABLE public.usuario DROP COLUMN IF EXISTS operador_saas;
