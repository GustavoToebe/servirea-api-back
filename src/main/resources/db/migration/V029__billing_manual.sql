-- V029__billing_manual.sql
-- Financeiro da paróquia no backoffice (seções 62/63/112 e 131.3 do plano
-- mestre). Estrutura plano -> preco_plano -> assinatura -> cobranca, com
-- pagamento registrado À MÃO pelo operador (PIX manual, sem gateway —
-- 131.3 item 15). Substitui o "marcar como pago" da V027, que só gravava
-- tenant.ultimo_pagamento_em sem histórico (a coluna continua existindo e
-- passa a ser atualizada pelo registro de pagamento).
--
-- Tabelas GLOBAIS, como backoffice_log: sem @TenantId. O operador age sobre
-- o negócio sem TenantContext; tenant_id aqui é FK comum, não isolamento.
-- Decidido com o usuário em 23/09/2026:
--   * catálogo de planos com preço vigente editável; a assinatura COPIA o
--     valor (e aceita valor negociado) — preço novo não mexe em contrato
--     antigo (seção 63);
--   * cobranças geradas por período (mensal = 1 por mês; anual = 1 por
--     ano cobrindo 12 meses);
--   * bloqueio automático após 3 dias de atraso NÃO entra agora — só
--     sinalizar; o bloqueio continua manual.

CREATE TYPE public.plano_periodicidade AS ENUM ('MENSAL', 'ANUAL');
CREATE TYPE public.forma_pagamento AS ENUM (
    'PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'DINHEIRO', 'TRANSFERENCIA', 'BOLETO', 'OUTRO');
CREATE TYPE public.assinatura_status AS ENUM ('ATIVA', 'CANCELADA');
CREATE TYPE public.cobranca_status AS ENUM ('ABERTA', 'PAGA', 'CANCELADA');

CREATE TABLE public.plano (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo              text NOT NULL UNIQUE,
    nome                text NOT NULL,
    limite_voluntarios  integer CHECK (limite_voluntarios IS NULL OR limite_voluntarios > 0),
    ativo               boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.plano IS
    'Catálogo de planos do SaaS (seção 63). Standard/Pro semeados sem preço — valores em R$ ainda não definidos (131.3).';

-- Sem preço de propósito: o operador cadastra em /admin/planos.
INSERT INTO public.plano (codigo, nome) VALUES
    ('STANDARD', 'Standard'),
    ('PRO', 'Pro');

CREATE TABLE public.preco_plano (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plano_id        uuid NOT NULL REFERENCES public.plano(id),
    periodicidade   public.plano_periodicidade NOT NULL,
    valor           numeric(10, 2) NOT NULL CHECK (valor >= 0),
    vigente_desde   date NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (plano_id, periodicidade, vigente_desde)
);

COMMENT ON TABLE public.preco_plano IS
    'Histórico de preços (seção 63: "Plano X 2026 -> preço A, 2027 -> preço B"). Vigente = maior vigente_desde <= hoje.';

CREATE TABLE public.assinatura (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES public.tenant(id),
    plano_id        uuid NOT NULL REFERENCES public.plano(id),
    periodicidade   public.plano_periodicidade NOT NULL,
    valor           numeric(10, 2) NOT NULL CHECK (valor >= 0),
    dia_vencimento  integer NOT NULL CHECK (dia_vencimento BETWEEN 1 AND 28),
    inicio          date NOT NULL,
    fim             date,
    status          public.assinatura_status NOT NULL DEFAULT 'ATIVA',
    observacoes     text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CHECK (fim IS NULL OR fim >= inicio)
);

-- Uma assinatura ATIVA por paróquia; trocar de plano encerra a anterior.
CREATE UNIQUE INDEX uq_assinatura_ativa_por_tenant
    ON public.assinatura (tenant_id) WHERE status = 'ATIVA';

CREATE TRIGGER trg_assinatura_updated_at
    BEFORE UPDATE ON public.assinatura
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

COMMENT ON COLUMN public.assinatura.valor IS
    'Valor acordado por período — copiado do preço vigente na contratação, ajustável (desconto/negociado).';

CREATE TABLE public.cobranca (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assinatura_id       uuid NOT NULL REFERENCES public.assinatura(id),
    tenant_id           uuid NOT NULL REFERENCES public.tenant(id),
    competencia_inicio  date NOT NULL,
    competencia_fim     date NOT NULL,
    vencimento          date NOT NULL,
    valor               numeric(10, 2) NOT NULL CHECK (valor >= 0),
    status              public.cobranca_status NOT NULL DEFAULT 'ABERTA',
    pago_em             date,
    valor_pago          numeric(10, 2) CHECK (valor_pago IS NULL OR valor_pago >= 0),
    forma_pagamento     public.forma_pagamento,
    observacao          text,
    registrado_por      uuid REFERENCES public.usuario(id) ON DELETE SET NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CHECK (competencia_fim >= competencia_inicio),
    CHECK (status <> 'PAGA' OR (pago_em IS NOT NULL AND valor_pago IS NOT NULL AND forma_pagamento IS NOT NULL)),
    -- Idempotência da geração (job diário + abrir a tela ao mesmo tempo).
    UNIQUE (assinatura_id, competencia_inicio)
);

CREATE INDEX idx_cobranca_tenant_status_vencimento
    ON public.cobranca (tenant_id, status, vencimento);

CREATE TRIGGER trg_cobranca_updated_at
    BEFORE UPDATE ON public.cobranca
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

COMMENT ON TABLE public.cobranca IS
    'Uma linha por período cobrado (mês no plano mensal, 12 meses no anual). Pagamento registrado à mão no backoffice (PIX manual, 131.3).';

-- RLS ligado SEM policy: no Supabase, tabela em "public" sem RLS fica
-- legível/gravável pela Data API (PostgREST) com a chave anon. Sem policy,
-- anon/authenticated não veem nada; a API Java conecta como dona das
-- tabelas (quem roda o Flyway) e não é afetada pelo RLS.
ALTER TABLE public.plano ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.preco_plano ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.assinatura ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cobranca ENABLE ROW LEVEL SECURITY;
