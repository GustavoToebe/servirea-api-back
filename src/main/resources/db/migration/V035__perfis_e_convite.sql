-- Etapa 1 da Central: perfis por paróquia e convite.
-- usuario_tenant.role permanece para o código que ainda autentica
-- pelo papel antigo. O filtro usa o perfil quando perfil_id está preenchido.
-- As tabelas de billing e backoffice não são apagadas aqui: a Central
-- ainda não existe para recebê-las, e a produção só é recriada no corte.

CREATE TABLE public.perfil (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    nome          text NOT NULL,
    ativo         boolean NOT NULL DEFAULT true,
    acesso_total  boolean NOT NULL DEFAULT false,
    sistema       boolean NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT perfil_nome_por_paroquia UNIQUE (tenant_id, nome)
);

COMMENT ON TABLE public.perfil IS
    'Perfil de acesso da paróquia. acesso_total libera o catálogo inteiro, inclusive permissão criada depois. sistema marca o Administrador, que não pode ser inativado.';

CREATE TABLE public.perfil_permissao (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    perfil_id  uuid NOT NULL REFERENCES public.perfil(id) ON DELETE CASCADE,
    permissao  text NOT NULL,
    CONSTRAINT perfil_permissao_unica UNIQUE (perfil_id, permissao)
);

CREATE INDEX idx_perfil_tenant ON public.perfil (tenant_id);

CREATE TRIGGER trg_perfil_updated_at
    BEFORE UPDATE ON public.perfil
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE public.perfil ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.perfil_permissao ENABLE ROW LEVEL SECURITY;

INSERT INTO public.perfil (tenant_id, nome, ativo, acesso_total, sistema)
SELECT id, 'Administrador', true, true, true FROM public.tenant;

INSERT INTO public.perfil (tenant_id, nome, ativo, acesso_total, sistema)
SELECT id, 'Secretário', true, false, false FROM public.tenant;

INSERT INTO public.perfil (tenant_id, nome, ativo, acesso_total, sistema)
SELECT id, 'Coordenador', true, false, false FROM public.tenant;

INSERT INTO public.perfil_permissao (perfil_id, permissao)
SELECT p.id, x.permissao
FROM public.perfil p
CROSS JOIN (VALUES
    ('PESSOA'), ('PESSOA_CRIAR'), ('PESSOA_ALTERAR'), ('PESSOA_EXCLUIR'), ('PESSOA_ATIVAR_INATIVAR'),
    ('PAROQUIA'),
    ('INSCRICAO'), ('INSCRICAO_ALTERAR'), ('INSCRICAO_APROVAR'), ('INSCRICAO_REJEITAR'),
    ('ESCALA'), ('VAGA')
) AS x(permissao)
WHERE p.nome = 'Secretário';

INSERT INTO public.perfil_permissao (perfil_id, permissao)
SELECT p.id, x.permissao
FROM public.perfil p
CROSS JOIN (VALUES
    ('ESCALA'), ('ESCALA_CRIAR'), ('ESCALA_ALTERAR'), ('ESCALA_EXCLUIR'),
    ('ESCALA_FINALIZAR_REABRIR'), ('ESCALA_CANCELAR'),
    ('VAGA'), ('VAGA_ALOCAR'), ('VAGA_PRESENCA'),
    ('PESSOA'), ('INSCRICAO')
) AS x(permissao)
WHERE p.nome = 'Coordenador';

ALTER TABLE public.usuario_tenant
    ADD COLUMN perfil_id uuid REFERENCES public.perfil(id);

UPDATE public.usuario_tenant ut
   SET perfil_id = p.id
  FROM public.perfil p
 WHERE p.tenant_id = ut.tenant_id
   AND p.nome = CASE ut.role::text
        WHEN 'ADMIN' THEN 'Administrador'
        WHEN 'COORDENADOR' THEN 'Coordenador'
        ELSE 'Secretário'
   END;

CREATE INDEX idx_usuario_tenant_perfil ON public.usuario_tenant (perfil_id);

ALTER TABLE public.usuario
    ADD COLUMN telefone text,
    ADD COLUMN tipo_telefone text;

ALTER TABLE public.password_reset_token
    ADD COLUMN finalidade text NOT NULL DEFAULT 'RESET';

COMMENT ON COLUMN public.password_reset_token.finalidade IS
    'RESET ou CONVITE. O convite também define a senha; o usuário nunca recebe senha pronta.';
