-- V030__pessoa_contato_relacao.sql
-- Cadastro pessoa-primeiro: identidade compartilhada, e-mails/telefones 1:N
-- (paróquia e pessoa) e relação responsável↔voluntário no espírito do
-- condominorelacionamento (rótulo "é" + rótulo inverso "de").

CREATE TYPE public.pessoa_papel AS ENUM ('VOLUNTARIO', 'RESPONSAVEL');

-- ---------------------------------------------------------------------------
-- pessoa
-- ---------------------------------------------------------------------------
CREATE TABLE public.pessoa (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid NOT NULL REFERENCES public.tenant(id),
    papel            public.pessoa_papel NOT NULL,
    nome_completo    text NOT NULL,
    data_nascimento  date,
    sexo             text,
    cpf              text,
    rg               text,
    cep              text,
    cidade           text,
    uf               text,
    logradouro       text,
    numero           text,
    complemento      text,
    bairro           text,
    observacoes      text,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pessoa_tenant_id_id_key UNIQUE (tenant_id, id)
);

CREATE INDEX idx_pessoa_tenant_id ON public.pessoa (tenant_id);
CREATE INDEX idx_pessoa_tenant_papel ON public.pessoa (tenant_id, papel);
CREATE INDEX idx_pessoa_nome_fts
    ON public.pessoa USING gin (to_tsvector('portuguese', nome_completo));

CREATE TRIGGER trg_pessoa_updated_at
    BEFORE UPDATE ON public.pessoa
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

COMMENT ON TABLE public.pessoa IS
    'Identidade do cadastro (voluntário ou responsável — papéis exclusivos).';

-- ---------------------------------------------------------------------------
-- pessoa_email / pessoa_telefone
-- ---------------------------------------------------------------------------
CREATE TABLE public.pessoa_email (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES public.tenant(id),
    pessoa_id   uuid NOT NULL,
    tipo        text NOT NULL,
    email       text NOT NULL,
    principal   boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pessoa_email_pessoa_fkey
        FOREIGN KEY (tenant_id, pessoa_id) REFERENCES public.pessoa (tenant_id, id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_pessoa_email_principal
    ON public.pessoa_email (pessoa_id) WHERE principal = true;
CREATE INDEX idx_pessoa_email_tenant ON public.pessoa_email (tenant_id);
CREATE INDEX idx_pessoa_email_lookup
    ON public.pessoa_email (tenant_id, lower(email)) WHERE principal = true;

CREATE TABLE public.pessoa_telefone (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES public.tenant(id),
    pessoa_id   uuid NOT NULL,
    tipo        text NOT NULL,
    numero      text NOT NULL,
    principal   boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pessoa_telefone_pessoa_fkey
        FOREIGN KEY (tenant_id, pessoa_id) REFERENCES public.pessoa (tenant_id, id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_pessoa_telefone_principal
    ON public.pessoa_telefone (pessoa_id) WHERE principal = true;
CREATE INDEX idx_pessoa_telefone_tenant ON public.pessoa_telefone (tenant_id);

-- ---------------------------------------------------------------------------
-- pessoa_relacao (responsável ↔ voluntário, rótulos nos dois sentidos)
-- ---------------------------------------------------------------------------
CREATE TABLE public.pessoa_relacao (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES public.tenant(id),
    responsavel_id       uuid NOT NULL,
    voluntario_id        uuid NOT NULL,
    parentesco           text NOT NULL,
    parentesco_inverso   text,
    principal            boolean NOT NULL DEFAULT false,
    created_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pessoa_relacao_distintos CHECK (responsavel_id <> voluntario_id),
    CONSTRAINT pessoa_relacao_responsavel_fkey
        FOREIGN KEY (tenant_id, responsavel_id) REFERENCES public.pessoa (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT pessoa_relacao_voluntario_fkey
        FOREIGN KEY (tenant_id, voluntario_id) REFERENCES public.pessoa (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT pessoa_relacao_par_key UNIQUE (responsavel_id, voluntario_id)
);

CREATE UNIQUE INDEX uq_pessoa_relacao_principal
    ON public.pessoa_relacao (voluntario_id) WHERE principal = true;
CREATE INDEX idx_pessoa_relacao_tenant ON public.pessoa_relacao (tenant_id);
CREATE INDEX idx_pessoa_relacao_responsavel ON public.pessoa_relacao (responsavel_id);
CREATE INDEX idx_pessoa_relacao_voluntario ON public.pessoa_relacao (voluntario_id);

COMMENT ON COLUMN public.pessoa_relacao.parentesco IS
    'Rótulo do lado do responsável (ex.: Pai, Mãe, Tia) — o "é" da tela.';
COMMENT ON COLUMN public.pessoa_relacao.parentesco_inverso IS
    'Rótulo do lado do voluntário (ex.: Filho) — o inverso do "é / de".';

-- ---------------------------------------------------------------------------
-- tenant_email / tenant_telefone (globais, sem @TenantId)
-- ---------------------------------------------------------------------------
CREATE TABLE public.tenant_email (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    tipo        text NOT NULL,
    email       text NOT NULL,
    principal   boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_tenant_email_principal
    ON public.tenant_email (tenant_id) WHERE principal = true;
CREATE INDEX idx_tenant_email_tenant ON public.tenant_email (tenant_id);

CREATE TABLE public.tenant_telefone (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    tipo        text NOT NULL,
    numero      text NOT NULL,
    principal   boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_tenant_telefone_principal
    ON public.tenant_telefone (tenant_id) WHERE principal = true;
CREATE INDEX idx_tenant_telefone_tenant ON public.tenant_telefone (tenant_id);

-- ---------------------------------------------------------------------------
-- inscrição: endereços extras + tabelas de contato
-- ---------------------------------------------------------------------------
ALTER TABLE public.inscricoes
    ADD COLUMN sexo text,
    ADD COLUMN cpf text,
    ADD COLUMN rg text,
    ADD COLUMN cep text,
    ADD COLUMN cidade text,
    ADD COLUMN uf text,
    ADD COLUMN complemento text;

CREATE TABLE public.inscricao_email (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL REFERENCES public.tenant(id),
    inscricao_id  uuid NOT NULL,
    tipo          text NOT NULL,
    email         text NOT NULL,
    principal     boolean NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT inscricao_email_inscricao_fkey
        FOREIGN KEY (tenant_id, inscricao_id) REFERENCES public.inscricoes (tenant_id, id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_inscricao_email_principal
    ON public.inscricao_email (inscricao_id) WHERE principal = true;

CREATE TABLE public.inscricao_telefone (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL REFERENCES public.tenant(id),
    inscricao_id  uuid NOT NULL,
    tipo          text NOT NULL,
    numero        text NOT NULL,
    principal     boolean NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT inscricao_telefone_inscricao_fkey
        FOREIGN KEY (tenant_id, inscricao_id) REFERENCES public.inscricoes (tenant_id, id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_inscricao_telefone_principal
    ON public.inscricao_telefone (inscricao_id) WHERE principal = true;

CREATE TABLE public.inscricao_responsavel_email (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                uuid NOT NULL REFERENCES public.tenant(id),
    inscricao_responsavel_id uuid NOT NULL REFERENCES public.inscricao_responsaveis(id) ON DELETE CASCADE,
    tipo                     text NOT NULL,
    email                    text NOT NULL,
    principal                boolean NOT NULL DEFAULT false,
    created_at               timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_inscricao_responsavel_email_principal
    ON public.inscricao_responsavel_email (inscricao_responsavel_id) WHERE principal = true;

CREATE TABLE public.inscricao_responsavel_telefone (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                uuid NOT NULL REFERENCES public.tenant(id),
    inscricao_responsavel_id uuid NOT NULL REFERENCES public.inscricao_responsaveis(id) ON DELETE CASCADE,
    tipo                     text NOT NULL,
    numero                   text NOT NULL,
    principal                boolean NOT NULL DEFAULT false,
    created_at               timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_inscricao_responsavel_telefone_principal
    ON public.inscricao_responsavel_telefone (inscricao_responsavel_id) WHERE principal = true;

ALTER TABLE public.inscricao_responsaveis
    ADD COLUMN parentesco_inverso text;

-- ---------------------------------------------------------------------------
-- Backfill
-- ---------------------------------------------------------------------------
INSERT INTO public.pessoa (
    id, tenant_id, papel, nome_completo, data_nascimento,
    logradouro, numero, bairro, observacoes, created_at, updated_at)
SELECT
    v.id, v.tenant_id, 'VOLUNTARIO', v.nome_completo, v.data_nascimento,
    v.rua, v.numero, v.bairro, v.observacoes, v.created_at, v.updated_at
FROM public.voluntarios v;

INSERT INTO public.pessoa_email (tenant_id, pessoa_id, tipo, email, principal)
SELECT v.tenant_id, v.id, 'E-mail pessoal', v.email, true
FROM public.voluntarios v
WHERE v.email IS NOT NULL AND btrim(v.email) <> '';

INSERT INTO public.pessoa_telefone (tenant_id, pessoa_id, tipo, numero, principal)
SELECT v.tenant_id, v.id, 'Telefone', v.telefone, true
FROM public.voluntarios v
WHERE v.telefone IS NOT NULL AND btrim(v.telefone) <> '';

INSERT INTO public.pessoa_telefone (tenant_id, pessoa_id, tipo, numero, principal)
SELECT v.tenant_id, v.id, 'celular', v.celular,
       NOT EXISTS (
           SELECT 1 FROM public.pessoa_telefone t
           WHERE t.pessoa_id = v.id AND t.principal = true)
FROM public.voluntarios v
WHERE v.celular IS NOT NULL AND btrim(v.celular) <> '';

CREATE TEMP TABLE resp_canon (
    old_id     uuid NOT NULL,
    tenant_id  uuid NOT NULL,
    nome       text NOT NULL,
    email      text NOT NULL,
    created_at timestamptz NOT NULL,
    pessoa_id  uuid NOT NULL DEFAULT gen_random_uuid()
);

INSERT INTO resp_canon (old_id, tenant_id, nome, email, created_at)
SELECT DISTINCT ON (r.tenant_id, lower(btrim(r.email)))
    r.id, r.tenant_id, r.nome, r.email, r.created_at
FROM public.responsaveis r
WHERE r.email IS NOT NULL AND btrim(r.email) <> ''
ORDER BY r.tenant_id, lower(btrim(r.email)), r.principal DESC, r.created_at;

INSERT INTO public.pessoa (id, tenant_id, papel, nome_completo, created_at, updated_at)
SELECT c.pessoa_id, c.tenant_id, 'RESPONSAVEL', c.nome, c.created_at, c.created_at
FROM resp_canon c;

INSERT INTO public.pessoa_email (tenant_id, pessoa_id, tipo, email, principal)
SELECT c.tenant_id, c.pessoa_id, 'E-mail pessoal', c.email, true
FROM resp_canon c;

CREATE TEMP TABLE resp_pessoa_map (
    old_id    uuid PRIMARY KEY,
    pessoa_id uuid NOT NULL,
    tenant_id uuid NOT NULL
);

INSERT INTO resp_pessoa_map (old_id, pessoa_id, tenant_id)
SELECT r.id, c.pessoa_id, r.tenant_id
FROM public.responsaveis r
JOIN resp_canon c
  ON c.tenant_id = r.tenant_id
 AND lower(btrim(c.email)) = lower(btrim(r.email))
WHERE r.email IS NOT NULL AND btrim(r.email) <> '';

CREATE TEMP TABLE resp_sem_email AS
SELECT r.id AS old_id, gen_random_uuid() AS pessoa_id, r.tenant_id, r.nome, r.created_at
FROM public.responsaveis r
WHERE r.email IS NULL OR btrim(r.email) = '';

INSERT INTO public.pessoa (id, tenant_id, papel, nome_completo, created_at, updated_at)
SELECT s.pessoa_id, s.tenant_id, 'RESPONSAVEL', s.nome, s.created_at, s.created_at
FROM resp_sem_email s;

INSERT INTO resp_pessoa_map (old_id, pessoa_id, tenant_id)
SELECT s.old_id, s.pessoa_id, s.tenant_id
FROM resp_sem_email s;

-- Telefones dos responsáveis
INSERT INTO public.pessoa_telefone (tenant_id, pessoa_id, tipo, numero, principal)
SELECT DISTINCT ON (m.pessoa_id)
    r.tenant_id, m.pessoa_id, 'Telefone', r.telefone, true
FROM public.responsaveis r
JOIN resp_pessoa_map m ON m.old_id = r.id
WHERE r.telefone IS NOT NULL AND btrim(r.telefone) <> '';

INSERT INTO public.pessoa_telefone (tenant_id, pessoa_id, tipo, numero, principal)
SELECT r.tenant_id, m.pessoa_id, 'celular', r.celular,
       NOT EXISTS (SELECT 1 FROM public.pessoa_telefone t WHERE t.pessoa_id = m.pessoa_id AND t.principal)
FROM public.responsaveis r
JOIN resp_pessoa_map m ON m.old_id = r.id
WHERE r.celular IS NOT NULL AND btrim(r.celular) <> '';

INSERT INTO public.pessoa_relacao (
    tenant_id, responsavel_id, voluntario_id, parentesco, parentesco_inverso, principal)
SELECT r.tenant_id, m.pessoa_id, r.voluntario_id, r.parentesco, 'Filho', r.principal
FROM public.responsaveis r
JOIN resp_pessoa_map m ON m.old_id = r.id
ON CONFLICT (responsavel_id, voluntario_id) DO NOTHING;

-- Tenant contato
INSERT INTO public.tenant_email (tenant_id, tipo, email, principal)
SELECT t.id, COALESCE(NULLIF(btrim(t.tipo_email), ''), 'CONTATO'), t.email, true
FROM public.tenant t
WHERE t.email IS NOT NULL AND btrim(t.email) <> '';

INSERT INTO public.tenant_telefone (tenant_id, tipo, numero, principal)
SELECT t.id, 'Telefone', t.telefone, true
FROM public.tenant t
WHERE t.telefone IS NOT NULL AND btrim(t.telefone) <> '';

-- Inscrição: contato solto → tabelas
INSERT INTO public.inscricao_email (tenant_id, inscricao_id, tipo, email, principal)
SELECT i.tenant_id, i.id, 'E-mail pessoal', i.email, true
FROM public.inscricoes i
WHERE i.email IS NOT NULL AND btrim(i.email) <> '';

INSERT INTO public.inscricao_telefone (tenant_id, inscricao_id, tipo, numero, principal)
SELECT i.tenant_id, i.id, 'Telefone', i.telefone, true
FROM public.inscricoes i
WHERE i.telefone IS NOT NULL AND btrim(i.telefone) <> '';

INSERT INTO public.inscricao_telefone (tenant_id, inscricao_id, tipo, numero, principal)
SELECT i.tenant_id, i.id, 'celular', i.celular,
       NOT EXISTS (SELECT 1 FROM public.inscricao_telefone t WHERE t.inscricao_id = i.id AND t.principal)
FROM public.inscricoes i
WHERE i.celular IS NOT NULL AND btrim(i.celular) <> '';

INSERT INTO public.inscricao_responsavel_email (tenant_id, inscricao_responsavel_id, tipo, email, principal)
SELECT ir.tenant_id, ir.id, 'E-mail pessoal', ir.email, true
FROM public.inscricao_responsaveis ir
WHERE ir.email IS NOT NULL AND btrim(ir.email) <> '';

INSERT INTO public.inscricao_responsavel_telefone (tenant_id, inscricao_responsavel_id, tipo, numero, principal)
SELECT ir.tenant_id, ir.id, 'Telefone', ir.telefone, true
FROM public.inscricao_responsaveis ir
WHERE ir.telefone IS NOT NULL AND btrim(ir.telefone) <> '';

INSERT INTO public.inscricao_responsavel_telefone (tenant_id, inscricao_responsavel_id, tipo, numero, principal)
SELECT ir.tenant_id, ir.id, 'celular', ir.celular,
       NOT EXISTS (
           SELECT 1 FROM public.inscricao_responsavel_telefone t
           WHERE t.inscricao_responsavel_id = ir.id AND t.principal)
FROM public.inscricao_responsaveis ir
WHERE ir.celular IS NOT NULL AND btrim(ir.celular) <> '';

-- ---------------------------------------------------------------------------
-- voluntarios vira perfil 1:1
-- ---------------------------------------------------------------------------
DROP INDEX IF EXISTS public.idx_voluntarios_nome_fts;

ALTER TABLE public.voluntarios
    ADD CONSTRAINT voluntarios_pessoa_fkey
        FOREIGN KEY (tenant_id, id) REFERENCES public.pessoa (tenant_id, id);

ALTER TABLE public.voluntarios
    DROP COLUMN nome_completo,
    DROP COLUMN data_nascimento,
    DROP COLUMN rua,
    DROP COLUMN numero,
    DROP COLUMN bairro,
    DROP COLUMN telefone,
    DROP COLUMN celular,
    DROP COLUMN email,
    DROP COLUMN observacoes;

DROP TABLE public.responsaveis;

ALTER TABLE public.inscricoes
    DROP COLUMN telefone,
    DROP COLUMN celular,
    DROP COLUMN email;

ALTER TABLE public.inscricao_responsaveis
    DROP COLUMN telefone,
    DROP COLUMN celular,
    DROP COLUMN email;

ALTER TABLE public.tenant
    DROP COLUMN email,
    DROP COLUMN telefone,
    DROP COLUMN tipo_email;

-- RLS sem policy (Data API anon não lê)
ALTER TABLE public.pessoa ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pessoa_email ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pessoa_telefone ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pessoa_relacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tenant_email ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tenant_telefone ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inscricao_email ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inscricao_telefone ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inscricao_responsavel_email ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inscricao_responsavel_telefone ENABLE ROW LEVEL SECURITY;
