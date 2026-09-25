-- Etapa 2 da Central: cópia local de direitos, idempotência, nonce e suporte.
-- Tabelas globais (sem @TenantId): o filtro de integração e o Kill Switch
-- leem fora do contexto de uma paróquia. RLS sem policy esconde da Data API.

CREATE TABLE public.direitos_locais (
    tenant_id         uuid PRIMARY KEY REFERENCES public.tenant(id) ON DELETE CASCADE,
    contratacao_id    uuid NOT NULL,
    versao            integer NOT NULL,
    situacao          text NOT NULL,
    acesso_liberado   boolean NOT NULL,
    motivo_bloqueio   text,
    vigente_ate       date,
    plano_codigo      text,
    plano_nome        text,
    limites           jsonb,
    funcionalidades   text[] NOT NULL DEFAULT '{}',
    confirmado_em     timestamptz NOT NULL,
    atualizado_em     timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.direitos_locais IS
    'Última versão dos direitos confirmada pela Central. Login e requisição leem daqui; a Central fora do ar não derruba a paróquia até a tolerância (72h).';

CREATE TABLE public.integracao_operacao (
    idempotency_key  text PRIMARY KEY,
    tipo             text NOT NULL,
    hash_corpo       text NOT NULL,
    status_http      integer NOT NULL,
    resposta         jsonb NOT NULL,
    criado_em        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.integracao_nonce (
    chave_id      text NOT NULL,
    nonce         text NOT NULL,
    recebido_em   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (chave_id, nonce)
);

CREATE INDEX idx_integracao_nonce_recebido ON public.integracao_nonce (recebido_em);

CREATE TABLE public.suporte_codigo (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    hash_codigo     text NOT NULL UNIQUE,
    operador_nome   text NOT NULL,
    operador_email  text NOT NULL,
    motivo          text NOT NULL,
    expira_em       timestamptz NOT NULL,
    usado_em        timestamptz
);

ALTER TABLE public.direitos_locais ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.integracao_operacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.integracao_nonce ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.suporte_codigo ENABLE ROW LEVEL SECURITY;
