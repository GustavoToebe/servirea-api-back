-- Comunicado por e-mail e WhatsApp (PLANO-005): configuração do WhatsApp por
-- paróquia, comunicado, destinatários (a fila) e anexos. Todas tenant-aware,
-- com FKs compostas (tenant_id, id) e RLS ligado (regra da V039).

CREATE TABLE public.paroquia_whatsapp (
    tenant_id  uuid PRIMARY KEY REFERENCES public.tenant(id) ON DELETE CASCADE,
    instancia  varchar(120) NOT NULL,
    token      varchar(300) NOT NULL,
    ativo      boolean NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.comunicado (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    canal        varchar(20) NOT NULL CHECK (canal IN ('EMAIL','WHATSAPP')),
    layout_id    uuid,
    layout_nome  varchar(120) NOT NULL,
    assunto      varchar(200),
    enviar_para  varchar(20) NOT NULL CHECK (enviar_para IN ('PESSOA','RESPONSAVEIS','AMBOS')),
    status       varchar(20) NOT NULL DEFAULT 'NA_FILA' CHECK (status IN ('NA_FILA','ENVIANDO','CONCLUIDO')),
    total        integer NOT NULL DEFAULT 0,
    enviados     integer NOT NULL DEFAULT 0,
    falhas       integer NOT NULL DEFAULT 0,
    criado_por   uuid REFERENCES public.usuario(id) ON DELETE SET NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    concluido_em timestamptz,
    CONSTRAINT comunicado_tenant_id_id_key UNIQUE (tenant_id, id),
    CONSTRAINT fk_comunicado_layout FOREIGN KEY (tenant_id, layout_id)
        REFERENCES public.layout_envio (tenant_id, id) ON DELETE SET NULL (layout_id)
);
CREATE INDEX idx_comunicado_tenant_created ON public.comunicado (tenant_id, created_at DESC);

CREATE TABLE public.comunicado_destinatario (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    comunicado_id uuid NOT NULL,
    pessoa_id     uuid,
    nome          varchar(200) NOT NULL,
    destino       varchar(200) NOT NULL,
    conteudo      text NOT NULL,
    assunto       varchar(200),
    status        varchar(20) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE','ENVIADO','FALHA')),
    tentativas    integer NOT NULL DEFAULT 0,
    erro          varchar(500),
    enviado_em    timestamptz,
    CONSTRAINT fk_destinatario_comunicado FOREIGN KEY (tenant_id, comunicado_id)
        REFERENCES public.comunicado (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_destinatario_pessoa FOREIGN KEY (tenant_id, pessoa_id)
        REFERENCES public.pessoa (tenant_id, id) ON DELETE SET NULL (pessoa_id)
);
CREATE INDEX idx_destinatario_tenant_status ON public.comunicado_destinatario (tenant_id, status);
CREATE INDEX idx_destinatario_comunicado ON public.comunicado_destinatario (comunicado_id);

CREATE TABLE public.comunicado_anexo (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    comunicado_id uuid NOT NULL,
    nome          varchar(200) NOT NULL,
    tipo          varchar(100) NOT NULL,
    tamanho       integer NOT NULL,
    conteudo      bytea,
    CONSTRAINT fk_anexo_comunicado FOREIGN KEY (tenant_id, comunicado_id)
        REFERENCES public.comunicado (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX idx_anexo_comunicado ON public.comunicado_anexo (comunicado_id);

ALTER TABLE public.paroquia_whatsapp ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comunicado ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comunicado_destinatario ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comunicado_anexo ENABLE ROW LEVEL SECURITY;
