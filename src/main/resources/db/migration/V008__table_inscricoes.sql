-- V008__table_inscricoes.sql
-- Fonte: seção 9.2 (dump) + seção 20.5 (CHECK constraints de auditoria e
-- UNIQUE em voluntario_id, confirmados ao vivo — esta tabela está AUSENTE
-- do schema.sql versionado no repositório do front-end; só existe em
-- produção, e só foi totalmente documentada nesta reconstrução).
--
-- NOTA sobre o trigger de updated_at: diferente de voluntarios/escalas
-- (que usam public.set_updated_at), esta tabela é gerida via as RPCs do
-- schema `private` (V011) — por isso o trigger aqui usa private.set_updated_at.
-- Essa escolha específica é inferência (ver nota de proveniência em V002),
-- não um fato extraído literalmente do painel.

CREATE TABLE public.inscricoes (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nome_completo        text NOT NULL,
    data_nascimento      date,
    tipo                 public.tipo_voluntario NOT NULL DEFAULT 'COROINHA',
    foto_path            text,
    etapa_catequese      text,
    eucaristia_ano       text,
    crisma_ano           text,
    rua                  text,
    numero               text,
    bairro               text,
    telefone             text,
    celular              text,
    email                text,
    horario_estudo       text,
    observacoes          text,
    autoriza_whatsapp    boolean NOT NULL DEFAULT false,
    funcoes_habilitadas  public.funcao_escala[] NOT NULL DEFAULT '{}',
    status               public.status_inscricao NOT NULL DEFAULT 'PENDENTE',
    data_aprovacao       timestamptz,
    aprovado_por         uuid REFERENCES auth.users(id),
    voluntario_id        uuid REFERENCES public.voluntarios(id),
    data_rejeicao        timestamptz,
    rejeitado_por        uuid REFERENCES auth.users(id),
    motivo_rejeicao      text,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT inscricoes_voluntario_id_key UNIQUE (voluntario_id),
    CONSTRAINT inscricoes_aprovada_ck
        CHECK (status <> 'APROVADA' OR (voluntario_id IS NOT NULL AND data_aprovacao IS NOT NULL AND aprovado_por IS NOT NULL)),
    CONSTRAINT inscricoes_rejeitada_ck
        CHECK (status <> 'REJEITADA' OR (data_rejeicao IS NOT NULL AND rejeitado_por IS NOT NULL))
);

COMMENT ON TABLE public.inscricoes IS
    'Cadastros pendentes vindos do formulário público /inscricao. Só pode ser criada via RPC private.criar_inscricao_impl com service_role (ver V011/V013) — nunca por INSERT direto de anon nem authenticated.';

CREATE TRIGGER trg_inscricoes_updated_at
    BEFORE UPDATE ON public.inscricoes
    FOR EACH ROW
    EXECUTE FUNCTION private.set_updated_at();
