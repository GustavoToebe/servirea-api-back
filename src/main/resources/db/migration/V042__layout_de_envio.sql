CREATE TABLE public.layout_envio (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES public.tenant(id),
    nome varchar(120) NOT NULL,
    tipo_layout varchar(30) NOT NULL CHECK (tipo_layout IN ('TODOS','RESPONSAVEL','COROINHA','ACOLITO','COROINHA_ACOLITO','MINISTRO')),
    tipo_envio varchar(20) NOT NULL CHECK (tipo_envio IN ('EMAIL','WHATSAPP')),
    assunto varchar(200),
    conteudo text NOT NULL CHECK (char_length(conteudo) <= 50000),
    ativo boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT layout_envio_tenant_id_id_key UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX idx_layout_envio_tenant_nome ON public.layout_envio (tenant_id, lower(nome));

CREATE TRIGGER trg_layout_envio_updated_at
    BEFORE UPDATE ON public.layout_envio
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE public.layout_envio ENABLE ROW LEVEL SECURITY;
