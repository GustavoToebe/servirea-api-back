-- V003__table_voluntarios.sql
-- Fonte: documento técnico, seção 9.2 (dump de produção) + seção 20.5
-- (índices/constraints confirmados ao vivo).

CREATE TABLE public.voluntarios (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nome_completo        text NOT NULL,
    data_nascimento      date,
    tipo                 public.tipo_voluntario NOT NULL DEFAULT 'COROINHA',
    ativo                boolean NOT NULL DEFAULT true,
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
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT voluntarios_horario_estudo_check
        CHECK (horario_estudo IS NULL OR horario_estudo IN ('MANHA', 'TARDE', 'NOITE'))
);

COMMENT ON TABLE public.voluntarios IS
    'Coroinhas/acólitos ativos e inativos. Dados de menores de idade — tratar como sensível (LGPD, seção 58 do plano mestre).';

-- Índices confirmados em schema.sql / seção 9.2 do documento técnico.
-- Observação preservada do levantamento original: o front-end filtra por
-- nome com `ilike %nome%`, não usa full-text search — o índice GIN existe
-- mas não é efetivamente aproveitado pela aplicação atual (dívida técnica
-- já registrada, não é motivo para omitir o índice na reconstrução).
CREATE INDEX idx_voluntarios_nome_fts
    ON public.voluntarios USING gin (to_tsvector('portuguese', nome_completo));

CREATE INDEX idx_voluntarios_ativo ON public.voluntarios (ativo);
CREATE INDEX idx_voluntarios_tipo ON public.voluntarios (tipo);

CREATE TRIGGER trg_voluntarios_updated_at
    BEFORE UPDATE ON public.voluntarios
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();
