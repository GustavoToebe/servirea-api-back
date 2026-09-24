-- V031__pessoa_papeis_flexiveis.sql
-- Uma pessoa pode ser voluntário E responsável (ministro adulto que também
-- é pai/mãe de coroinha). Responsável deixa de ser obrigatório no cadastro
-- interno — adulto/ministro entra sem relação.

ALTER TABLE public.pessoa
    ADD COLUMN e_voluntario boolean NOT NULL DEFAULT false,
    ADD COLUMN e_responsavel boolean NOT NULL DEFAULT false;

UPDATE public.pessoa
   SET e_voluntario = (papel = 'VOLUNTARIO'),
       e_responsavel = (papel = 'RESPONSAVEL');

ALTER TABLE public.pessoa
    DROP COLUMN papel,
    ADD CONSTRAINT ck_pessoa_pelo_menos_um_papel CHECK (e_voluntario OR e_responsavel);

DROP INDEX IF EXISTS public.idx_pessoa_tenant_papel;
CREATE INDEX idx_pessoa_tenant_voluntario ON public.pessoa (tenant_id) WHERE e_voluntario;
CREATE INDEX idx_pessoa_tenant_responsavel ON public.pessoa (tenant_id) WHERE e_responsavel;

COMMENT ON TABLE public.pessoa IS
    'Identidade do cadastro. Papéis não são exclusivos: e_voluntario e e_responsavel podem coexistir.';
COMMENT ON COLUMN public.pessoa.e_voluntario IS
    'Tem (ou terá) perfil em voluntarios. Relação com responsável é opcional.';
COMMENT ON COLUMN public.pessoa.e_responsavel IS
    'Pode aparecer no lado responsável de pessoa_relacao.';
