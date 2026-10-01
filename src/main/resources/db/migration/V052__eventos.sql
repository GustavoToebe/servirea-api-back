-- Módulo de Eventos (01/10/2026): evento com local e fotos, inscrição de pessoas já
-- cadastradas e as mensagens de WhatsApp (confirmação e lembrete), que vão pela fila
-- dos comunicados (a inscrição guarda o id do destinatário para mostrar a situação).
-- Tenant-aware, FKs compostas (tenant_id, id) e RLS ligado (regra da V039).

CREATE TABLE public.evento (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    titulo                varchar(150) NOT NULL,
    descricao             text,
    inicio                timestamp NOT NULL,
    termino               timestamp,
    local_nome            varchar(150),
    cep                   varchar(9),
    logradouro            varchar(200),
    numero                varchar(20),
    complemento           varchar(100),
    bairro                varchar(100),
    cidade                varchar(100),
    uf                    varchar(2),
    mapa_url              varchar(500),
    vagas                 integer CHECK (vagas IS NULL OR vagas > 0),
    responsavel_nome      varchar(150),
    responsavel_telefone  varchar(20),
    lembrete_dias         integer NOT NULL DEFAULT 1 CHECK (lembrete_dias BETWEEN 0 AND 30),
    mensagem_confirmacao  text NOT NULL,
    mensagem_lembrete     text NOT NULL,
    situacao              varchar(20) NOT NULL DEFAULT 'RASCUNHO'
                          CHECK (situacao IN ('RASCUNHO','PUBLICADO','CANCELADO')),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT evento_tenant_id_id_key UNIQUE (tenant_id, id),
    CONSTRAINT evento_termino_depois_do_inicio CHECK (termino IS NULL OR termino >= inicio)
);
CREATE INDEX idx_evento_tenant_inicio ON public.evento (tenant_id, inicio);

CREATE TABLE public.evento_foto (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    evento_id  uuid NOT NULL,
    caminho    varchar(300) NOT NULL,
    capa       boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_evento_foto_evento FOREIGN KEY (tenant_id, evento_id)
        REFERENCES public.evento (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX idx_evento_foto_evento ON public.evento_foto (evento_id);

CREATE TABLE public.evento_inscricao (
    id                           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                    uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    evento_id                    uuid NOT NULL,
    pessoa_id                    uuid NOT NULL,
    autoriza_whatsapp            boolean NOT NULL,
    confirmacao_destinatario_id  uuid,
    lembrete_destinatario_id     uuid,
    lembrete_enviado_em          timestamptz,
    created_at                   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT evento_inscricao_unica UNIQUE (evento_id, pessoa_id),
    CONSTRAINT fk_evento_inscricao_evento FOREIGN KEY (tenant_id, evento_id)
        REFERENCES public.evento (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_evento_inscricao_pessoa FOREIGN KEY (tenant_id, pessoa_id)
        REFERENCES public.pessoa (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX idx_evento_inscricao_evento ON public.evento_inscricao (evento_id);

ALTER TABLE public.evento ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.evento_foto ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.evento_inscricao ENABLE ROW LEVEL SECURITY;
