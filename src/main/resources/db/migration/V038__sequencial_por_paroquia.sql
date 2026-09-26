-- Número curto por paróquia (teste de telas de 26/09/2026): pessoa, escala,
-- inscrição, perfil e vínculo de usuário ganham um "sequencial" que começa em
-- 1 em cada paróquia, para ditar e copiar. O id continua sendo o uuid.
--
-- Quem numera é o banco: um contador por (paróquia, tabela) e um gatilho
-- BEFORE INSERT. A linha do contador fica travada até o commit, então dois
-- cadastros simultâneos na mesma paróquia nunca pegam o mesmo número.
-- Nome "sequencial" porque pessoa e inscricoes já têm "numero" (do endereço).

CREATE TABLE public.tenant_sequencial (
    tenant_id  uuid NOT NULL REFERENCES public.tenant (id),
    tabela     varchar(40) NOT NULL,
    ultimo     bigint NOT NULL,
    PRIMARY KEY (tenant_id, tabela)
);
ALTER TABLE public.tenant_sequencial ENABLE ROW LEVEL SECURITY;

CREATE FUNCTION public.proximo_sequencial() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.sequencial IS NULL THEN
        INSERT INTO public.tenant_sequencial AS s (tenant_id, tabela, ultimo)
        VALUES (NEW.tenant_id, TG_TABLE_NAME, 1)
        ON CONFLICT (tenant_id, tabela) DO UPDATE SET ultimo = s.ultimo + 1
        RETURNING s.ultimo INTO NEW.sequencial;
    END IF;
    RETURN NEW;
END
$$;

-- pessoa
ALTER TABLE public.pessoa ADD COLUMN sequencial bigint;
UPDATE public.pessoa t SET sequencial = x.n
  FROM (SELECT id, row_number() OVER (PARTITION BY tenant_id ORDER BY created_at, id) AS n FROM public.pessoa) x
 WHERE t.id = x.id;
INSERT INTO public.tenant_sequencial (tenant_id, tabela, ultimo)
SELECT tenant_id, 'pessoa', max(sequencial) FROM public.pessoa GROUP BY tenant_id;
ALTER TABLE public.pessoa ALTER COLUMN sequencial SET NOT NULL;
ALTER TABLE public.pessoa ADD CONSTRAINT uq_pessoa_sequencial UNIQUE (tenant_id, sequencial);
CREATE TRIGGER trg_pessoa_sequencial BEFORE INSERT ON public.pessoa
    FOR EACH ROW EXECUTE FUNCTION public.proximo_sequencial();

-- escalas
ALTER TABLE public.escalas ADD COLUMN sequencial bigint;
UPDATE public.escalas t SET sequencial = x.n
  FROM (SELECT id, row_number() OVER (PARTITION BY tenant_id ORDER BY created_at, id) AS n FROM public.escalas) x
 WHERE t.id = x.id;
INSERT INTO public.tenant_sequencial (tenant_id, tabela, ultimo)
SELECT tenant_id, 'escalas', max(sequencial) FROM public.escalas GROUP BY tenant_id;
ALTER TABLE public.escalas ALTER COLUMN sequencial SET NOT NULL;
ALTER TABLE public.escalas ADD CONSTRAINT uq_escalas_sequencial UNIQUE (tenant_id, sequencial);
CREATE TRIGGER trg_escalas_sequencial BEFORE INSERT ON public.escalas
    FOR EACH ROW EXECUTE FUNCTION public.proximo_sequencial();

-- inscricoes
ALTER TABLE public.inscricoes ADD COLUMN sequencial bigint;
UPDATE public.inscricoes t SET sequencial = x.n
  FROM (SELECT id, row_number() OVER (PARTITION BY tenant_id ORDER BY created_at, id) AS n FROM public.inscricoes) x
 WHERE t.id = x.id;
INSERT INTO public.tenant_sequencial (tenant_id, tabela, ultimo)
SELECT tenant_id, 'inscricoes', max(sequencial) FROM public.inscricoes GROUP BY tenant_id;
ALTER TABLE public.inscricoes ALTER COLUMN sequencial SET NOT NULL;
ALTER TABLE public.inscricoes ADD CONSTRAINT uq_inscricoes_sequencial UNIQUE (tenant_id, sequencial);
CREATE TRIGGER trg_inscricoes_sequencial BEFORE INSERT ON public.inscricoes
    FOR EACH ROW EXECUTE FUNCTION public.proximo_sequencial();

-- perfil
ALTER TABLE public.perfil ADD COLUMN sequencial bigint;
UPDATE public.perfil t SET sequencial = x.n
  FROM (SELECT id, row_number() OVER (PARTITION BY tenant_id ORDER BY created_at, id) AS n FROM public.perfil) x
 WHERE t.id = x.id;
INSERT INTO public.tenant_sequencial (tenant_id, tabela, ultimo)
SELECT tenant_id, 'perfil', max(sequencial) FROM public.perfil GROUP BY tenant_id;
ALTER TABLE public.perfil ALTER COLUMN sequencial SET NOT NULL;
ALTER TABLE public.perfil ADD CONSTRAINT uq_perfil_sequencial UNIQUE (tenant_id, sequencial);
CREATE TRIGGER trg_perfil_sequencial BEFORE INSERT ON public.perfil
    FOR EACH ROW EXECUTE FUNCTION public.proximo_sequencial();

-- usuario_tenant (o usuário é global; o número é do vínculo com a paróquia)
ALTER TABLE public.usuario_tenant ADD COLUMN sequencial bigint;
UPDATE public.usuario_tenant t SET sequencial = x.n
  FROM (SELECT usuario_id, tenant_id,
               row_number() OVER (PARTITION BY tenant_id ORDER BY created_at, usuario_id) AS n
          FROM public.usuario_tenant) x
 WHERE t.usuario_id = x.usuario_id AND t.tenant_id = x.tenant_id;
INSERT INTO public.tenant_sequencial (tenant_id, tabela, ultimo)
SELECT tenant_id, 'usuario_tenant', max(sequencial) FROM public.usuario_tenant GROUP BY tenant_id;
ALTER TABLE public.usuario_tenant ALTER COLUMN sequencial SET NOT NULL;
ALTER TABLE public.usuario_tenant ADD CONSTRAINT uq_usuario_tenant_sequencial UNIQUE (tenant_id, sequencial);
CREATE TRIGGER trg_usuario_tenant_sequencial BEFORE INSERT ON public.usuario_tenant
    FOR EACH ROW EXECUTE FUNCTION public.proximo_sequencial();
