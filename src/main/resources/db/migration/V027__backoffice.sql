-- V027__backoffice.sql
-- Backoffice do SaaS (seção 111 do plano mestre — painel do operador da
-- plataforma, distinto do ADMIN da paróquia). Três mudanças no schema:
--
-- 1. usuario.operador_saas — o mesmo login (ex.: suporte@servirea.com.br)
--    entra no painel SEM tenant no JWT. Não se cria um usuario_tenant em
--    todas as paróquias (seções 60/61/99: operador não mistura com o
--    usuário da paróquia).
-- 2. tenant ganha contato/endereço (cadastro da paróquia no painel) e
--    ultimo_pagamento_em (PIX manual, seção 131.3 — "marcar como pago"
--    sem tabela de billing ainda; plano/assinatura/cobranca ficam na
--    Fase 12).
-- 3. backoffice_log — trilha GLOBAL das ações do painel (criar paróquia,
--    bloquear, marcar pago, entrar em suporte). Distinta de audit_log,
--    que é tenant-aware (@TenantId) e não serve para listar "todas as
--    paróquias" sem furar o Native Query Gate (seção 81).

ALTER TABLE public.usuario
    ADD COLUMN operador_saas boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN public.usuario.operador_saas IS
    'Operador do SaaS (backoffice, seção 111). Login em /admin/auth/login; JWT purpose=backoffice sem tenant. Nunca entra pelo /auth/login da paróquia.';

ALTER TABLE public.tenant
    ADD COLUMN email text,
    ADD COLUMN telefone text,
    ADD COLUMN cep text,
    ADD COLUMN cidade text,
    ADD COLUMN uf text,
    ADD COLUMN bairro text,
    ADD COLUMN logradouro text,
    ADD COLUMN numero text,
    ADD COLUMN complemento text,
    ADD COLUMN observacoes text,
    ADD COLUMN ultimo_pagamento_em timestamptz;

COMMENT ON COLUMN public.tenant.ultimo_pagamento_em IS
    'Última vez que o operador marcou o PIX como recebido (MVP sem gateway, seção 131.3). Não substitui a tabela cobranca da Fase 12.';

CREATE TABLE public.backoffice_log (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid REFERENCES public.usuario(id) ON DELETE SET NULL,
    tenant_alvo_id  uuid REFERENCES public.tenant(id) ON DELETE SET NULL,
    acao            text NOT NULL,
    entidade        text NOT NULL,
    entidade_id     uuid NOT NULL,
    ip              text,
    request_id      text,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_backoffice_log_created_at ON public.backoffice_log (created_at DESC);
CREATE INDEX idx_backoffice_log_tenant_alvo ON public.backoffice_log (tenant_alvo_id, created_at DESC);

COMMENT ON TABLE public.backoffice_log IS
    'Ações do painel do operador (seção 111). Tabela global, sem tenant_id de isolamento — o operador age sobre o negócio, não dentro de uma paróquia.';
