-- Esquema do banco, gerado das migrations (V001-V065). NÃO editar à mão.
-- Para regerar: scripts/gerar-schema.ps1 (precisa do Postgres local com a API em dev já ter subido).
-- Só o schema public, sem dono e sem permissões. O banco de produção é criado pelo Flyway a partir destas migrations.

--
--

\restrict CpkY1bsg7NRcGj8o6gpceP3NAgdKMU5jLbka7HeWnwnZzaUBg1gXpd2o9YvFZKX



--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA public;


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: condicao_especial; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.condicao_especial AS ENUM (
    'SINDROME_DOWN',
    'TEA',
    'TDAH',
    'ANSIEDADE',
    'DEPRESSAO',
    'BORDERLINE',
    'OUTRA'
);


--
-- Name: funcao_escala; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.funcao_escala AS ENUM (
    'MISSAL',
    'CRUZ',
    'CREDENCIA',
    'VELA',
    'COLETA',
    'SINO',
    'OUTRO'
);


--
-- Name: periodo_dia; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.periodo_dia AS ENUM (
    'MANHA',
    'TARDE',
    'NOITE'
);


--
-- Name: presenca_vaga; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.presenca_vaga AS ENUM (
    'PENDENTE',
    'PRESENTE',
    'FALTOU'
);


--
-- Name: status_escala; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.status_escala AS ENUM (
    'RASCUNHO',
    'FINALIZADA',
    'CANCELADA'
);


--
-- Name: status_inscricao; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.status_inscricao AS ENUM (
    'PENDENTE',
    'APROVADA',
    'REJEITADA'
);


--
-- Name: tenant_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.tenant_status AS ENUM (
    'ATIVO',
    'TRIAL',
    'BLOQUEADO',
    'CANCELADO'
);


--
-- Name: tipo_escala; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.tipo_escala AS ENUM (
    'SEMANAL',
    'MENSAL'
);


--
-- Name: tipo_voluntario; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.tipo_voluntario AS ENUM (
    'COROINHA',
    'ACOLITO',
    'AMBOS',
    'MESC'
);


--
-- Name: usuario_tenant_role; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.usuario_tenant_role AS ENUM (
    'ADMIN',
    'COORDENADOR',
    'VISUALIZADOR'
);


--
-- Name: usuario_tenant_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.usuario_tenant_status AS ENUM (
    'ATIVO',
    'INATIVO'
);


--
-- Name: aprovar_inscricao(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.aprovar_inscricao(p_inscricao_id uuid) RETURNS uuid
    LANGUAGE sql SECURITY DEFINER
    SET search_path TO ''
    AS $$
    SELECT private.aprovar_inscricao_impl(p_inscricao_id);
$$;


--
-- Name: criar_inscricao_publica(jsonb, jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.criar_inscricao_publica(p_dados jsonb, p_responsaveis jsonb) RETURNS uuid
    LANGUAGE sql SECURITY DEFINER
    SET search_path TO ''
    AS $$
    SELECT private.criar_inscricao_impl(p_dados, p_responsaveis);
$$;


--
-- Name: proximo_sequencial(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.proximo_sequencial() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: rejeitar_inscricao(uuid, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.rejeitar_inscricao(p_inscricao_id uuid, p_motivo text DEFAULT NULL::text) RETURNS boolean
    LANGUAGE sql SECURITY DEFINER
    SET search_path TO ''
    AS $$
    SELECT private.rejeitar_inscricao_impl(p_inscricao_id, p_motivo);
$$;


--
-- Name: set_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;




--
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    user_id uuid,
    acao text NOT NULL,
    entidade text NOT NULL,
    entidade_id uuid NOT NULL,
    changed_fields text[],
    ip text,
    request_id text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE audit_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.audit_log IS 'Trilha de auditoria (seção 59) — quem fez o quê, em qual entidade, sem duplicar o conteúdo pessoal em si (só os nomes dos campos alterados).';


--
-- Name: calendario_assinatura; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.calendario_assinatura (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    pessoa_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    expira_em timestamp with time zone NOT NULL,
    criado_em timestamp with time zone NOT NULL
);


--
-- Name: comunicado; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comunicado (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    canal character varying(20) NOT NULL,
    layout_id uuid,
    layout_nome character varying(120) NOT NULL,
    assunto character varying(200),
    enviar_para character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'NA_FILA'::character varying NOT NULL,
    total integer DEFAULT 0 NOT NULL,
    enviados integer DEFAULT 0 NOT NULL,
    falhas integer DEFAULT 0 NOT NULL,
    criado_por uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    concluido_em timestamp with time zone,
    CONSTRAINT comunicado_canal_check CHECK (((canal)::text = ANY ((ARRAY['EMAIL'::character varying, 'WHATSAPP'::character varying])::text[]))),
    CONSTRAINT comunicado_enviar_para_check CHECK (((enviar_para)::text = ANY ((ARRAY['PESSOA'::character varying, 'RESPONSAVEIS'::character varying, 'AMBOS'::character varying])::text[]))),
    CONSTRAINT comunicado_status_check CHECK (((status)::text = ANY ((ARRAY['NA_FILA'::character varying, 'ENVIANDO'::character varying, 'CONCLUIDO'::character varying])::text[])))
);


--
-- Name: comunicado_anexo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comunicado_anexo (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    comunicado_id uuid NOT NULL,
    nome character varying(200) NOT NULL,
    tipo character varying(100) NOT NULL,
    tamanho integer NOT NULL,
    conteudo bytea
);


--
-- Name: comunicado_destinatario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comunicado_destinatario (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    comunicado_id uuid NOT NULL,
    pessoa_id uuid,
    nome character varying(200) NOT NULL,
    destino character varying(200) NOT NULL,
    conteudo text NOT NULL,
    assunto character varying(200),
    status character varying(20) DEFAULT 'PENDENTE'::character varying NOT NULL,
    tentativas integer DEFAULT 0 NOT NULL,
    erro character varying(500),
    enviado_em timestamp with time zone,
    proxima_tentativa timestamp with time zone DEFAULT '1970-01-01 00:00:00+00'::timestamp with time zone NOT NULL,
    reservado_por uuid,
    reserva_ate timestamp with time zone,
    cota_competencia date,
    CONSTRAINT ck_comunicado_destinatario_reserva CHECK (((reservado_por IS NULL) = (reserva_ate IS NULL))),
    CONSTRAINT comunicado_destinatario_cota_competencia_check CHECK ((EXTRACT(day FROM cota_competencia) = (1)::numeric)),
    CONSTRAINT comunicado_destinatario_status_check CHECK (((status)::text = ANY ((ARRAY['PENDENTE'::character varying, 'ENVIADO'::character varying, 'FALHA'::character varying])::text[])))
);


--
-- Name: diocese; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.diocese (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nome text NOT NULL,
    uf text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE diocese; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.diocese IS 'Diocese que agrupa paróquias. Só informativa: sem cota nem regra de acesso.';


--
-- Name: direitos_locais; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.direitos_locais (
    tenant_id uuid NOT NULL,
    contratacao_id uuid NOT NULL,
    versao integer NOT NULL,
    situacao text NOT NULL,
    acesso_liberado boolean NOT NULL,
    motivo_bloqueio text,
    vigente_ate date,
    plano_codigo text,
    plano_nome text,
    limites jsonb,
    funcionalidades text[] DEFAULT '{}'::text[] NOT NULL,
    confirmado_em timestamp with time zone NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE direitos_locais; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.direitos_locais IS 'Última versão dos direitos confirmada pela Central. Login e requisição leem daqui; a Central fora do ar não derruba a paróquia até a tolerância (72h).';


--
-- Name: disponibilidade_voluntario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.disponibilidade_voluntario (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    voluntario_id uuid NOT NULL,
    dia_semana text,
    data date,
    periodo public.periodo_dia NOT NULL,
    observacao text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT disponibilidade_voluntario_dia_semana_check CHECK ((dia_semana = ANY (ARRAY['MONDAY'::text, 'TUESDAY'::text, 'WEDNESDAY'::text, 'THURSDAY'::text, 'FRIDAY'::text, 'SATURDAY'::text, 'SUNDAY'::text]))),
    CONSTRAINT disponibilidade_voluntario_dia_xor_data CHECK (((dia_semana IS NOT NULL) <> (data IS NOT NULL)))
);


--
-- Name: TABLE disponibilidade_voluntario; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.disponibilidade_voluntario IS 'Dias/horários em que um voluntário pode servir — filtro adicional planejado para o picker de candidatos (seção 49), ainda não integrado (ver README.md).';


--
-- Name: escala_candidatura; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.escala_candidatura (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    escala_id uuid NOT NULL,
    vaga_id uuid NOT NULL,
    pessoa_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    vaga_versao bigint NOT NULL,
    situacao character varying(20) NOT NULL,
    versao bigint DEFAULT 0 NOT NULL,
    criada_em timestamp with time zone NOT NULL,
    atualizada_em timestamp with time zone NOT NULL,
    celebracao character varying(255) NOT NULL,
    inicio timestamp without time zone NOT NULL,
    funcao character varying(20) NOT NULL,
    CONSTRAINT escala_candidatura_situacao_check CHECK (((situacao)::text = ANY ((ARRAY['PENDENTE'::character varying, 'APROVADA'::character varying, 'RECUSADA'::character varying, 'DESISTIDA'::character varying, 'EXPIRADA'::character varying])::text[])))
);


--
-- Name: escala_eventos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.escala_eventos (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    escala_id uuid NOT NULL,
    data date NOT NULL,
    horario time without time zone NOT NULL,
    celebracao text DEFAULT 'Missa'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid NOT NULL,
    referencia boolean DEFAULT false NOT NULL
);


--
-- Name: TABLE escala_eventos; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.escala_eventos IS 'Um evento (missa) dentro de uma escala. O front-end atual apaga e recria todos os eventos de uma escala a cada save (seção 46/16.8 do plano mestre) — comportamento preservado nesta reconstrução, não corrigido aqui.';


--
-- Name: escala_resposta_historico; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.escala_resposta_historico (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    escala_id uuid NOT NULL,
    vaga_id uuid NOT NULL,
    pessoa_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    resposta character varying(20) NOT NULL,
    respondido_em timestamp with time zone NOT NULL,
    versao bigint NOT NULL,
    CONSTRAINT escala_resposta_historico_resposta_check CHECK (((resposta)::text = ANY ((ARRAY['CONFIRMADA'::character varying, 'RECUSADA'::character varying])::text[])))
);


--
-- Name: escala_troca; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.escala_troca (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    escala_id uuid NOT NULL,
    vaga_id uuid NOT NULL,
    solicitante_id uuid NOT NULL,
    substituto_id uuid NOT NULL,
    solicitante_usuario_id uuid NOT NULL,
    substituto_usuario_id uuid NOT NULL,
    vaga_versao bigint NOT NULL,
    situacao character varying(30) NOT NULL,
    versao bigint DEFAULT 0 NOT NULL,
    criada_em timestamp with time zone NOT NULL,
    atualizada_em timestamp with time zone NOT NULL,
    celebracao character varying(255) NOT NULL,
    inicio timestamp without time zone NOT NULL,
    funcao character varying(20) NOT NULL,
    CONSTRAINT escala_troca_check CHECK ((solicitante_id <> substituto_id)),
    CONSTRAINT escala_troca_situacao_check CHECK (((situacao)::text = ANY ((ARRAY['AGUARDANDO_ACEITE'::character varying, 'ACEITA'::character varying, 'APROVADA'::character varying, 'RECUSADA'::character varying, 'CANCELADA'::character varying, 'EXPIRADA'::character varying])::text[])))
);


--
-- Name: escala_vagas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.escala_vagas (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    evento_id uuid NOT NULL,
    funcao public.funcao_escala NOT NULL,
    posicao integer DEFAULT 1 NOT NULL,
    voluntario_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid NOT NULL,
    presenca public.presenca_vaga DEFAULT 'PENDENTE'::public.presenca_vaga NOT NULL,
    resposta character varying(20) DEFAULT 'PENDENTE'::character varying NOT NULL,
    resposta_em timestamp with time zone,
    resposta_versao bigint DEFAULT 0 NOT NULL,
    CONSTRAINT escala_vagas_posicao_check CHECK ((posicao > 0)),
    CONSTRAINT escala_vagas_resposta_check CHECK (((resposta)::text = ANY ((ARRAY['PENDENTE'::character varying, 'CONFIRMADA'::character varying, 'RECUSADA'::character varying])::text[])))
);


--
-- Name: COLUMN escala_vagas.presenca; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.escala_vagas.presenca IS 'Registro de presença do voluntário nesta vaga, feito pelo coordenador após o evento acontecer. PENDENTE é o valor inicial (evento ainda não ocorreu, ou ainda não foi conferido) — não deve ser confundido com "faltou".';


--
-- Name: escalas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.escalas (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    titulo text NOT NULL,
    tipo public.tipo_escala NOT NULL,
    ano integer,
    mes integer,
    status public.status_escala DEFAULT 'RASCUNHO'::public.status_escala NOT NULL,
    observacao text,
    created_by uuid DEFAULT auth.uid(),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    sequencial bigint NOT NULL,
    colunas jsonb,
    layout_id uuid,
    CONSTRAINT escalas_ano_check CHECK (((ano >= 2020) AND (ano <= 2100))),
    CONSTRAINT escalas_mes_check CHECK (((mes >= 1) AND (mes <= 12)))
);


--
-- Name: evento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    titulo character varying(150) NOT NULL,
    descricao text,
    inicio timestamp without time zone NOT NULL,
    termino timestamp without time zone,
    local_nome character varying(150),
    cep character varying(9),
    logradouro character varying(200),
    numero character varying(20),
    complemento character varying(100),
    bairro character varying(100),
    cidade character varying(100),
    uf character varying(2),
    mapa_url character varying(500),
    vagas integer,
    responsavel_nome character varying(150),
    responsavel_telefone character varying(20),
    lembrete_dias character varying(50) DEFAULT '1'::character varying NOT NULL,
    mensagem_confirmacao text,
    mensagem_lembrete text,
    situacao character varying(20) DEFAULT 'RASCUNHO'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    whatsapp_habilitado boolean DEFAULT true NOT NULL,
    whatsapp_layout_confirmacao_id uuid,
    whatsapp_layout_lembrete_id uuid,
    email_habilitado boolean DEFAULT false NOT NULL,
    email_layout_confirmacao_id uuid,
    email_layout_lembrete_id uuid,
    CONSTRAINT evento_situacao_check CHECK (((situacao)::text = ANY ((ARRAY['RASCUNHO'::character varying, 'PUBLICADO'::character varying, 'CANCELADO'::character varying])::text[]))),
    CONSTRAINT evento_termino_depois_do_inicio CHECK (((termino IS NULL) OR (termino >= inicio))),
    CONSTRAINT evento_vagas_check CHECK (((vagas IS NULL) OR (vagas > 0)))
);


--
-- Name: evento_foto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento_foto (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    evento_id uuid NOT NULL,
    caminho character varying(300) NOT NULL,
    capa boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    foto_tamanho_bytes bigint,
    CONSTRAINT evento_foto_foto_tamanho_bytes_check CHECK ((foto_tamanho_bytes > 0))
);


--
-- Name: evento_inscricao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento_inscricao (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    evento_id uuid NOT NULL,
    pessoa_id uuid NOT NULL,
    autoriza_whatsapp boolean NOT NULL,
    confirmacao_destinatario_id uuid,
    lembrete_destinatario_id uuid,
    lembrete_enviado_em timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    confirmacao_email_destinatario_id uuid,
    lembrete_email_destinatario_id uuid,
    lembrete_email_enviado_em timestamp with time zone
);


--
-- Name: fila_envio_janela; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fila_envio_janela (
    id character varying(100) NOT NULL,
    proximo_permitido timestamp with time zone DEFAULT '1970-01-01 00:00:00+00'::timestamp with time zone NOT NULL,
    reservado_por uuid,
    reserva_ate timestamp with time zone,
    CONSTRAINT ck_janela_reserva CHECK (((reservado_por IS NULL) = (reserva_ate IS NULL)))
);


--
-- Name: financeiro_categoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.financeiro_categoria (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    nome character varying(120) NOT NULL,
    ativo boolean DEFAULT true NOT NULL
);


--
-- Name: financeiro_conta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.financeiro_conta (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    nome character varying(120) NOT NULL,
    saldo_inicial numeric(14,2) DEFAULT 0 NOT NULL,
    data_saldo_inicial date NOT NULL,
    ativo boolean DEFAULT true NOT NULL
);


--
-- Name: financeiro_movimento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.financeiro_movimento (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    versao bigint DEFAULT 0 NOT NULL,
    descricao character varying(200) NOT NULL,
    tipo character varying(20) NOT NULL,
    situacao character varying(20) DEFAULT 'PENDENTE'::character varying NOT NULL,
    valor numeric(14,2) NOT NULL,
    vencimento date NOT NULL,
    data_pagamento date,
    conta_id uuid NOT NULL,
    categoria_id uuid NOT NULL,
    observacoes character varying(1000),
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT financeiro_movimento_check CHECK ((((situacao)::text = 'PAGO'::text) = (data_pagamento IS NOT NULL))),
    CONSTRAINT financeiro_movimento_situacao_check CHECK (((situacao)::text = ANY ((ARRAY['PENDENTE'::character varying, 'PAGO'::character varying, 'CANCELADO'::character varying])::text[]))),
    CONSTRAINT financeiro_movimento_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['RECEITA'::character varying, 'DESPESA'::character varying])::text[]))),
    CONSTRAINT financeiro_movimento_valor_check CHECK ((valor > (0)::numeric))
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: importacao_pessoa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.importacao_pessoa (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    chave uuid NOT NULL,
    hash_arquivo character varying(64) NOT NULL,
    quantidade integer NOT NULL,
    competencia date NOT NULL,
    criado_em timestamp with time zone NOT NULL,
    CONSTRAINT importacao_pessoa_competencia_check CHECK ((EXTRACT(day FROM competencia) = (1)::numeric)),
    CONSTRAINT importacao_pessoa_quantidade_check CHECK (((quantidade >= 1) AND (quantidade <= 100)))
);


--
-- Name: indisponibilidade_mes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.indisponibilidade_mes (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    ano integer NOT NULL,
    mes integer NOT NULL,
    versao bigint DEFAULT 0 NOT NULL,
    CONSTRAINT indisponibilidade_mes_ano_check CHECK (((ano >= 2000) AND (ano <= 2100))),
    CONSTRAINT indisponibilidade_mes_mes_check CHECK (((mes >= 1) AND (mes <= 12))),
    CONSTRAINT indisponibilidade_mes_versao_check CHECK ((versao >= 0))
);


--
-- Name: indisponibilidade_voluntario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.indisponibilidade_voluntario (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    voluntario_id uuid NOT NULL,
    data date NOT NULL,
    periodo public.periodo_dia,
    observacao character varying(200),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: inscricao_email; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inscricao_email (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    inscricao_id uuid NOT NULL,
    tipo text NOT NULL,
    email text NOT NULL,
    principal boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: inscricao_responsaveis; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inscricao_responsaveis (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inscricao_id uuid NOT NULL,
    parentesco text NOT NULL,
    nome text NOT NULL,
    principal boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid NOT NULL,
    parentesco_inverso text
);


--
-- Name: inscricao_responsavel_email; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inscricao_responsavel_email (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    inscricao_responsavel_id uuid NOT NULL,
    tipo text NOT NULL,
    email text NOT NULL,
    principal boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: inscricao_responsavel_telefone; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inscricao_responsavel_telefone (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    inscricao_responsavel_id uuid NOT NULL,
    tipo text NOT NULL,
    numero text NOT NULL,
    principal boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: inscricao_telefone; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inscricao_telefone (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    inscricao_id uuid NOT NULL,
    tipo text NOT NULL,
    numero text NOT NULL,
    principal boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: inscricoes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inscricoes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nome_completo text NOT NULL,
    data_nascimento date,
    tipo public.tipo_voluntario DEFAULT 'COROINHA'::public.tipo_voluntario NOT NULL,
    foto_path text,
    etapa_catequese text,
    eucaristia_ano text,
    crisma_ano text,
    rua text,
    numero text,
    bairro text,
    horario_estudo text,
    observacoes text,
    autoriza_whatsapp boolean DEFAULT false NOT NULL,
    funcoes_habilitadas public.funcao_escala[] DEFAULT '{}'::public.funcao_escala[] NOT NULL,
    status public.status_inscricao DEFAULT 'PENDENTE'::public.status_inscricao NOT NULL,
    data_aprovacao timestamp with time zone,
    aprovado_por uuid,
    voluntario_id uuid,
    data_rejeicao timestamp with time zone,
    rejeitado_por uuid,
    motivo_rejeicao text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid NOT NULL,
    sexo text,
    cpf text,
    rg text,
    cep text,
    cidade text,
    uf text,
    complemento text,
    sequencial bigint NOT NULL,
    condicoes public.condicao_especial[] DEFAULT '{}'::public.condicao_especial[] NOT NULL,
    nivel_suporte_tea integer,
    condicao_outra character varying(200),
    cuidados character varying(1000),
    consentimento_cuidados_em timestamp with time zone,
    foto_tamanho_bytes bigint,
    CONSTRAINT inscricoes_aprovada_ck CHECK (((status <> 'APROVADA'::public.status_inscricao) OR ((voluntario_id IS NOT NULL) AND (data_aprovacao IS NOT NULL) AND (aprovado_por IS NOT NULL)))),
    CONSTRAINT inscricoes_foto_tamanho_bytes_check CHECK ((foto_tamanho_bytes > 0)),
    CONSTRAINT inscricoes_nivel_suporte_tea_check CHECK (((nivel_suporte_tea >= 1) AND (nivel_suporte_tea <= 3))),
    CONSTRAINT inscricoes_rejeitada_ck CHECK (((status <> 'REJEITADA'::public.status_inscricao) OR ((data_rejeicao IS NOT NULL) AND (rejeitado_por IS NOT NULL))))
);


--
-- Name: TABLE inscricoes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.inscricoes IS 'Cadastros pendentes vindos do formulário público /inscricao. Só pode ser criada via RPC private.criar_inscricao_impl com service_role (ver V011/V013) — nunca por INSERT direto de anon nem authenticated.';


--
-- Name: integracao_nonce; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integracao_nonce (
    chave_id text NOT NULL,
    nonce text NOT NULL,
    recebido_em timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: integracao_operacao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integracao_operacao (
    idempotency_key text NOT NULL,
    tipo text NOT NULL,
    hash_corpo text NOT NULL,
    status_http integer NOT NULL,
    resposta jsonb NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: layout_envio; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.layout_envio (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    nome character varying(120) NOT NULL,
    tipo_layout character varying(30) NOT NULL,
    tipo_envio character varying(20) NOT NULL,
    assunto character varying(200),
    conteudo text NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT layout_envio_conteudo_check CHECK ((char_length(conteudo) <= 50000)),
    CONSTRAINT layout_envio_tipo_envio_check CHECK (((tipo_envio)::text = ANY ((ARRAY['EMAIL'::character varying, 'WHATSAPP'::character varying])::text[]))),
    CONSTRAINT layout_envio_tipo_layout_check CHECK (((tipo_layout)::text = ANY ((ARRAY['TODOS'::character varying, 'RESPONSAVEL'::character varying, 'COROINHA'::character varying, 'ACOLITO'::character varying, 'COROINHA_ACOLITO'::character varying, 'MINISTRO'::character varying, 'EVENTO'::character varying])::text[])))
);


--
-- Name: layout_escala; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.layout_escala (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    nome character varying(255) NOT NULL,
    tipo public.tipo_escala NOT NULL,
    colunas jsonb NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    sistema boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    descricao text,
    padrao boolean DEFAULT false NOT NULL
);


--
-- Name: mural_aviso; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mural_aviso (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    versao bigint DEFAULT 0 NOT NULL,
    titulo character varying(160) NOT NULL,
    descricao character varying(4000) NOT NULL,
    status character varying(24) NOT NULL,
    prazo date,
    criado_em timestamp with time zone NOT NULL,
    atualizado_em timestamp with time zone NOT NULL,
    CONSTRAINT mural_aviso_status_check CHECK (((status)::text = ANY ((ARRAY['PUBLICADO'::character varying, 'ARQUIVADO'::character varying])::text[])))
);


--
-- Name: paroquia_whatsapp; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.paroquia_whatsapp (
    tenant_id uuid NOT NULL,
    instancia character varying(120) NOT NULL,
    token text NOT NULL,
    ativo boolean DEFAULT false NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: password_reset_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_token (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    token_hash text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    finalidade text DEFAULT 'RESET'::text NOT NULL
);


--
-- Name: TABLE password_reset_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.password_reset_token IS 'Tokens de "esqueci minha senha" (seção 32 do plano mestre) - nunca armazenar o token puro, só o hash (token_hash). used_at marca uso único: uma vez consumido (ou expirado), o token não pode ser reaproveitado.';


--
-- Name: COLUMN password_reset_token.finalidade; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.password_reset_token.finalidade IS 'RESET ou CONVITE. O convite também define a senha; o usuário nunca recebe senha pronta.';


--
-- Name: pastoral_equipe; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pastoral_equipe (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    nome character varying(120) NOT NULL,
    descricao character varying(1000),
    ativo boolean NOT NULL,
    versao bigint DEFAULT 0 NOT NULL
);


--
-- Name: pastoral_membro; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pastoral_membro (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    equipe_id uuid NOT NULL,
    pessoa_id uuid NOT NULL,
    papel character varying(20) NOT NULL,
    ativo boolean NOT NULL,
    versao bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pastoral_membro_papel_check CHECK (((papel)::text = ANY ((ARRAY['MEMBRO'::character varying, 'COORDENADOR'::character varying])::text[])))
);


--
-- Name: perfil; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.perfil (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    nome text NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    acesso_total boolean DEFAULT false NOT NULL,
    sistema boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    sequencial bigint NOT NULL
);


--
-- Name: TABLE perfil; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.perfil IS 'Perfil de acesso da paróquia. acesso_total libera o catálogo inteiro, inclusive permissão criada depois. sistema marca o Administrador, que não pode ser inativado.';


--
-- Name: perfil_permissao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.perfil_permissao (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    perfil_id uuid NOT NULL,
    permissao text NOT NULL
);


--
-- Name: pessoa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pessoa (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    nome_completo text NOT NULL,
    data_nascimento date,
    sexo text,
    cpf text,
    rg text,
    cep text,
    cidade text,
    uf text,
    logradouro text,
    numero text,
    complemento text,
    bairro text,
    observacoes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    e_voluntario boolean DEFAULT false NOT NULL,
    e_responsavel boolean DEFAULT false NOT NULL,
    sequencial bigint NOT NULL,
    condicoes public.condicao_especial[] DEFAULT '{}'::public.condicao_especial[] NOT NULL,
    nivel_suporte_tea integer,
    condicao_outra character varying(200),
    cuidados character varying(1000),
    CONSTRAINT ck_pessoa_pelo_menos_um_papel CHECK ((e_voluntario OR e_responsavel)),
    CONSTRAINT pessoa_nivel_suporte_tea_check CHECK (((nivel_suporte_tea >= 1) AND (nivel_suporte_tea <= 3)))
);


--
-- Name: TABLE pessoa; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pessoa IS 'Identidade do cadastro. Papéis não são exclusivos: e_voluntario e e_responsavel podem coexistir.';


--
-- Name: COLUMN pessoa.e_voluntario; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pessoa.e_voluntario IS 'Tem (ou terá) perfil em voluntarios. Relação com responsável é opcional.';


--
-- Name: COLUMN pessoa.e_responsavel; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pessoa.e_responsavel IS 'Pode aparecer no lado responsável de pessoa_relacao.';


--
-- Name: pessoa_email; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pessoa_email (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    pessoa_id uuid NOT NULL,
    tipo text NOT NULL,
    email text NOT NULL,
    principal boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: pessoa_relacao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pessoa_relacao (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    responsavel_id uuid NOT NULL,
    voluntario_id uuid NOT NULL,
    parentesco text NOT NULL,
    parentesco_inverso text,
    principal boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pessoa_relacao_distintos CHECK ((responsavel_id <> voluntario_id))
);


--
-- Name: COLUMN pessoa_relacao.parentesco; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pessoa_relacao.parentesco IS 'Rótulo do lado do responsável (ex.: Pai, Mãe, Tia) — o "é" da tela.';


--
-- Name: COLUMN pessoa_relacao.parentesco_inverso; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pessoa_relacao.parentesco_inverso IS 'Rótulo do lado do voluntário (ex.: Filho) — o inverso do "é / de".';


--
-- Name: pessoa_telefone; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pessoa_telefone (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    pessoa_id uuid NOT NULL,
    tipo text NOT NULL,
    numero text NOT NULL,
    principal boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: refresh_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_token (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    token_hash text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    replaced_by uuid,
    ip text,
    user_agent text
);


--
-- Name: TABLE refresh_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.refresh_token IS 'Refresh tokens com rotation (seção 36 do plano mestre) - nunca armazenar o token puro, só o hash (token_hash). Reutilização de um token já revogado (revoked_at preenchido) deve ser tratada como evento de segurança quando a Fase 5 implementar o fluxo de refresh.';


--
-- Name: resposta_indisponibilidade; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.resposta_indisponibilidade (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    voluntario_id uuid NOT NULL,
    ano integer NOT NULL,
    mes integer NOT NULL,
    sem_restricao boolean NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT resposta_indisponibilidade_mes_check CHECK (((mes >= 1) AND (mes <= 12)))
);


--
-- Name: suporte_codigo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.suporte_codigo (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    hash_codigo text NOT NULL,
    operador_nome text NOT NULL,
    operador_email text NOT NULL,
    motivo text NOT NULL,
    expira_em timestamp with time zone NOT NULL,
    usado_em timestamp with time zone
);


--
-- Name: tarefa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tarefa (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    versao bigint DEFAULT 0 NOT NULL,
    titulo character varying(160) NOT NULL,
    descricao character varying(4000) NOT NULL,
    status character varying(24) NOT NULL,
    prazo date,
    equipe character varying(120),
    criado_em timestamp with time zone NOT NULL,
    atualizado_em timestamp with time zone NOT NULL,
    responsavel_usuario_id uuid,
    CONSTRAINT tarefa_status_check CHECK (((status)::text = ANY ((ARRAY['ABERTA'::character varying, 'EM_ANDAMENTO'::character varying, 'CONCLUIDA'::character varying, 'CANCELADA'::character varying])::text[])))
);


--
-- Name: tenant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    codigo text NOT NULL,
    slug text NOT NULL,
    nome text NOT NULL,
    razao_social text,
    cnpj text,
    status public.tenant_status DEFAULT 'TRIAL'::public.tenant_status NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    cep text,
    cidade text,
    uf text,
    bairro text,
    logradouro text,
    numero text,
    complemento text,
    observacoes text,
    ultimo_pagamento_em timestamp with time zone,
    vigencia_ate date,
    diocese_id uuid
);


--
-- Name: TABLE tenant; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tenant IS 'Cada linha é uma paróquia (tenant) do SaaS. Tabela global do "Master lógico" (seção 25 do plano mestre) - não possui tenant_id, ela é a raiz da hierarquia multi-tenant.';


--
-- Name: COLUMN tenant.slug; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tenant.slug IS 'Identificador público usado em URLs (ex.: /public/paroquia-sao-jose/inscricoes, seção 21 do plano mestre).';


--
-- Name: COLUMN tenant.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tenant.status IS 'Autoridade central de acesso (Kill Switch, seção 28): ATIVO/TRIAL liberam acesso normal; BLOQUEADO/CANCELADO negam acesso totalmente (decidido em 21/09/2026 - sem modo somente leitura).';


--
-- Name: COLUMN tenant.ultimo_pagamento_em; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tenant.ultimo_pagamento_em IS 'Última vez que o operador marcou o PIX como recebido (MVP sem gateway, seção 131.3). Não substitui a tabela cobranca da Fase 12.';


--
-- Name: COLUMN tenant.vigencia_ate; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tenant.vigencia_ate IS 'Fim da vigência (trial de 7 dias na criação, seção 131.3; depois o operador ajusta / marcar pago). Usado no filtro "quando vai acabar".';


--
-- Name: COLUMN tenant.diocese_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tenant.diocese_id IS 'Diocese da paróquia. Nulo = sem vínculo; a cota diocesana não se aplica.';


--
-- Name: tenant_email; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_email (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    tipo text NOT NULL,
    email text NOT NULL,
    principal boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: tenant_sequencial; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_sequencial (
    tenant_id uuid NOT NULL,
    tabela character varying(40) NOT NULL,
    ultimo bigint NOT NULL
);


--
-- Name: tenant_telefone; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_telefone (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    tipo text NOT NULL,
    numero text NOT NULL,
    principal boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: usuario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email text NOT NULL,
    senha_hash text,
    nome text NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    telefone text,
    tipo_telefone text
);


--
-- Name: TABLE usuario; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.usuario IS 'Usuário global do SaaS (Master lógico, seção 25) - pode estar vinculado a mais de uma paróquia via usuario_tenant (seção 29).';


--
-- Name: COLUMN usuario.senha_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.usuario.senha_hash IS 'Hash Argon2 ou BCrypt (seção 32 do plano mestre) - nunca senha reversível. Nullable por enquanto: a Fase 5 (autenticação própria) é quem efetivamente popula/usa esta coluna; até lá a tabela existe só como parte do modelo.';


--
-- Name: usuario_tenant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario_tenant (
    usuario_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    role public.usuario_tenant_role DEFAULT 'VISUALIZADOR'::public.usuario_tenant_role NOT NULL,
    status public.usuario_tenant_status DEFAULT 'ATIVO'::public.usuario_tenant_status NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    perfil_id uuid,
    sequencial bigint NOT NULL,
    pessoa_id uuid
);


--
-- Name: TABLE usuario_tenant; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.usuario_tenant IS 'Vínculo N:N usuário<->paróquia (seção 29 do plano mestre). A chave primária composta já garante o UNIQUE (usuario_id, tenant_id) pedido na seção 29. Um usuário sem linha status=ATIVO aqui para um tenant não deve conseguir acessar aquele tenant (Kill Switch, seção 28).';


--
-- Name: voluntarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.voluntarios (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tipo public.tipo_voluntario DEFAULT 'COROINHA'::public.tipo_voluntario NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    foto_path text,
    etapa_catequese text,
    eucaristia_ano text,
    crisma_ano text,
    horario_estudo text,
    autoriza_whatsapp boolean DEFAULT false NOT NULL,
    funcoes_habilitadas public.funcao_escala[] DEFAULT '{}'::public.funcao_escala[] NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid NOT NULL,
    mandato_inicio date,
    mandato_fim date,
    foto_tamanho_bytes bigint,
    CONSTRAINT voluntarios_foto_tamanho_bytes_check CHECK ((foto_tamanho_bytes > 0)),
    CONSTRAINT voluntarios_horario_estudo_check CHECK (((horario_estudo IS NULL) OR (horario_estudo = ANY (ARRAY['MANHA'::text, 'TARDE'::text, 'NOITE'::text])))),
    CONSTRAINT voluntarios_mandato_ordem CHECK (((mandato_fim IS NULL) OR (mandato_inicio IS NULL) OR (mandato_fim >= mandato_inicio)))
);


--
-- Name: TABLE voluntarios; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.voluntarios IS 'Coroinhas/acólitos ativos e inativos. Dados de menores de idade — tratar como sensível (LGPD, seção 58 do plano mestre).';


--
-- Name: COLUMN voluntarios.mandato_inicio; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.voluntarios.mandato_inicio IS 'Data de investidura do mandato. Opcional; usada sobretudo no MESC.';


--
-- Name: COLUMN voluntarios.mandato_fim; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.voluntarios.mandato_fim IS 'Vencimento do mandato diocesano. Nulo = sem prazo cadastrado.';


--
-- Name: vw_voluntario_compromissos; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.vw_voluntario_compromissos WITH (security_invoker='true') AS
 SELECT v.voluntario_id,
    s.id AS escala_id,
    s.titulo AS escala_titulo,
    (s.status)::text AS escala_status,
    e.data,
    e.horario,
    e.celebracao,
    v.funcao
   FROM ((public.escala_vagas v
     JOIN public.escala_eventos e ON ((e.id = v.evento_id)))
     JOIN public.escalas s ON ((s.id = e.escala_id)))
  WHERE (v.voluntario_id IS NOT NULL);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);


--
-- Name: calendario_assinatura calendario_assinatura_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calendario_assinatura
    ADD CONSTRAINT calendario_assinatura_pkey PRIMARY KEY (id);


--
-- Name: calendario_assinatura calendario_assinatura_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calendario_assinatura
    ADD CONSTRAINT calendario_assinatura_token_hash_key UNIQUE (token_hash);


--
-- Name: comunicado_anexo comunicado_anexo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comunicado_anexo
    ADD CONSTRAINT comunicado_anexo_pkey PRIMARY KEY (id);


--
-- Name: comunicado_destinatario comunicado_destinatario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comunicado_destinatario
    ADD CONSTRAINT comunicado_destinatario_pkey PRIMARY KEY (id);


--
-- Name: comunicado comunicado_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comunicado
    ADD CONSTRAINT comunicado_pkey PRIMARY KEY (id);


--
-- Name: comunicado comunicado_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comunicado
    ADD CONSTRAINT comunicado_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: diocese diocese_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.diocese
    ADD CONSTRAINT diocese_pkey PRIMARY KEY (id);


--
-- Name: direitos_locais direitos_locais_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.direitos_locais
    ADD CONSTRAINT direitos_locais_pkey PRIMARY KEY (tenant_id);


--
-- Name: disponibilidade_voluntario disponibilidade_voluntario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disponibilidade_voluntario
    ADD CONSTRAINT disponibilidade_voluntario_pkey PRIMARY KEY (id);


--
-- Name: escala_candidatura escala_candidatura_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_candidatura
    ADD CONSTRAINT escala_candidatura_pkey PRIMARY KEY (id);


--
-- Name: escala_candidatura escala_candidatura_tenant_id_vaga_id_pessoa_id_vaga_versao_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_candidatura
    ADD CONSTRAINT escala_candidatura_tenant_id_vaga_id_pessoa_id_vaga_versao_key UNIQUE (tenant_id, vaga_id, pessoa_id, vaga_versao);


--
-- Name: escala_eventos escala_eventos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_eventos
    ADD CONSTRAINT escala_eventos_pkey PRIMARY KEY (id);


--
-- Name: escala_eventos escala_eventos_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_eventos
    ADD CONSTRAINT escala_eventos_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: escala_resposta_historico escala_resposta_historico_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_resposta_historico
    ADD CONSTRAINT escala_resposta_historico_pkey PRIMARY KEY (id);


--
-- Name: escala_troca escala_troca_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_troca
    ADD CONSTRAINT escala_troca_pkey PRIMARY KEY (id);


--
-- Name: escala_vagas escala_vagas_evento_id_funcao_posicao_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_vagas
    ADD CONSTRAINT escala_vagas_evento_id_funcao_posicao_key UNIQUE (evento_id, funcao, posicao);


--
-- Name: escala_vagas escala_vagas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_vagas
    ADD CONSTRAINT escala_vagas_pkey PRIMARY KEY (id);


--
-- Name: escalas escalas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escalas
    ADD CONSTRAINT escalas_pkey PRIMARY KEY (id);


--
-- Name: escalas escalas_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escalas
    ADD CONSTRAINT escalas_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: evento_foto evento_foto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_foto
    ADD CONSTRAINT evento_foto_pkey PRIMARY KEY (id);


--
-- Name: evento_inscricao evento_inscricao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_inscricao
    ADD CONSTRAINT evento_inscricao_pkey PRIMARY KEY (id);


--
-- Name: evento_inscricao evento_inscricao_unica; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_inscricao
    ADD CONSTRAINT evento_inscricao_unica UNIQUE (evento_id, pessoa_id);


--
-- Name: evento evento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento
    ADD CONSTRAINT evento_pkey PRIMARY KEY (id);


--
-- Name: evento evento_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento
    ADD CONSTRAINT evento_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: fila_envio_janela fila_envio_janela_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fila_envio_janela
    ADD CONSTRAINT fila_envio_janela_pkey PRIMARY KEY (id);


--
-- Name: financeiro_categoria financeiro_categoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financeiro_categoria
    ADD CONSTRAINT financeiro_categoria_pkey PRIMARY KEY (id);


--
-- Name: financeiro_categoria financeiro_categoria_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financeiro_categoria
    ADD CONSTRAINT financeiro_categoria_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: financeiro_conta financeiro_conta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financeiro_conta
    ADD CONSTRAINT financeiro_conta_pkey PRIMARY KEY (id);


--
-- Name: financeiro_conta financeiro_conta_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financeiro_conta
    ADD CONSTRAINT financeiro_conta_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: financeiro_movimento financeiro_movimento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financeiro_movimento
    ADD CONSTRAINT financeiro_movimento_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: importacao_pessoa importacao_pessoa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.importacao_pessoa
    ADD CONSTRAINT importacao_pessoa_pkey PRIMARY KEY (id);


--
-- Name: importacao_pessoa importacao_pessoa_tenant_id_chave_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.importacao_pessoa
    ADD CONSTRAINT importacao_pessoa_tenant_id_chave_key UNIQUE (tenant_id, chave);


--
-- Name: indisponibilidade_mes indisponibilidade_mes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indisponibilidade_mes
    ADD CONSTRAINT indisponibilidade_mes_pkey PRIMARY KEY (id);


--
-- Name: indisponibilidade_mes indisponibilidade_mes_tenant_id_ano_mes_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indisponibilidade_mes
    ADD CONSTRAINT indisponibilidade_mes_tenant_id_ano_mes_key UNIQUE (tenant_id, ano, mes);


--
-- Name: indisponibilidade_voluntario indisponibilidade_voluntario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indisponibilidade_voluntario
    ADD CONSTRAINT indisponibilidade_voluntario_pkey PRIMARY KEY (id);


--
-- Name: indisponibilidade_voluntario indisponibilidade_voluntario_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indisponibilidade_voluntario
    ADD CONSTRAINT indisponibilidade_voluntario_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: inscricao_email inscricao_email_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_email
    ADD CONSTRAINT inscricao_email_pkey PRIMARY KEY (id);


--
-- Name: inscricao_responsaveis inscricao_responsaveis_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_responsaveis
    ADD CONSTRAINT inscricao_responsaveis_pkey PRIMARY KEY (id);


--
-- Name: inscricao_responsavel_email inscricao_responsavel_email_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_responsavel_email
    ADD CONSTRAINT inscricao_responsavel_email_pkey PRIMARY KEY (id);


--
-- Name: inscricao_responsavel_telefone inscricao_responsavel_telefone_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_responsavel_telefone
    ADD CONSTRAINT inscricao_responsavel_telefone_pkey PRIMARY KEY (id);


--
-- Name: inscricao_telefone inscricao_telefone_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_telefone
    ADD CONSTRAINT inscricao_telefone_pkey PRIMARY KEY (id);


--
-- Name: inscricoes inscricoes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricoes
    ADD CONSTRAINT inscricoes_pkey PRIMARY KEY (id);


--
-- Name: inscricoes inscricoes_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricoes
    ADD CONSTRAINT inscricoes_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: inscricoes inscricoes_voluntario_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricoes
    ADD CONSTRAINT inscricoes_voluntario_id_key UNIQUE (voluntario_id);


--
-- Name: integracao_nonce integracao_nonce_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integracao_nonce
    ADD CONSTRAINT integracao_nonce_pkey PRIMARY KEY (chave_id, nonce);


--
-- Name: integracao_operacao integracao_operacao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integracao_operacao
    ADD CONSTRAINT integracao_operacao_pkey PRIMARY KEY (idempotency_key);


--
-- Name: layout_envio layout_envio_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.layout_envio
    ADD CONSTRAINT layout_envio_pkey PRIMARY KEY (id);


--
-- Name: layout_envio layout_envio_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.layout_envio
    ADD CONSTRAINT layout_envio_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: layout_escala layout_escala_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.layout_escala
    ADD CONSTRAINT layout_escala_pkey PRIMARY KEY (id);


--
-- Name: layout_escala layout_escala_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.layout_escala
    ADD CONSTRAINT layout_escala_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: mural_aviso mural_aviso_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mural_aviso
    ADD CONSTRAINT mural_aviso_pkey PRIMARY KEY (id);


--
-- Name: paroquia_whatsapp paroquia_whatsapp_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.paroquia_whatsapp
    ADD CONSTRAINT paroquia_whatsapp_pkey PRIMARY KEY (tenant_id);


--
-- Name: password_reset_token password_reset_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_token
    ADD CONSTRAINT password_reset_token_pkey PRIMARY KEY (id);


--
-- Name: password_reset_token password_reset_token_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_token
    ADD CONSTRAINT password_reset_token_token_hash_key UNIQUE (token_hash);


--
-- Name: pastoral_equipe pastoral_equipe_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pastoral_equipe
    ADD CONSTRAINT pastoral_equipe_pkey PRIMARY KEY (id);


--
-- Name: pastoral_equipe pastoral_equipe_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pastoral_equipe
    ADD CONSTRAINT pastoral_equipe_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: pastoral_membro pastoral_membro_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pastoral_membro
    ADD CONSTRAINT pastoral_membro_pkey PRIMARY KEY (id);


--
-- Name: pastoral_membro pastoral_membro_tenant_id_equipe_id_pessoa_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pastoral_membro
    ADD CONSTRAINT pastoral_membro_tenant_id_equipe_id_pessoa_id_key UNIQUE (tenant_id, equipe_id, pessoa_id);


--
-- Name: perfil perfil_nome_por_paroquia; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil
    ADD CONSTRAINT perfil_nome_por_paroquia UNIQUE (tenant_id, nome);


--
-- Name: perfil_permissao perfil_permissao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil_permissao
    ADD CONSTRAINT perfil_permissao_pkey PRIMARY KEY (id);


--
-- Name: perfil_permissao perfil_permissao_unica; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil_permissao
    ADD CONSTRAINT perfil_permissao_unica UNIQUE (perfil_id, permissao);


--
-- Name: perfil perfil_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil
    ADD CONSTRAINT perfil_pkey PRIMARY KEY (id);


--
-- Name: pessoa_email pessoa_email_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa_email
    ADD CONSTRAINT pessoa_email_pkey PRIMARY KEY (id);


--
-- Name: pessoa pessoa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa
    ADD CONSTRAINT pessoa_pkey PRIMARY KEY (id);


--
-- Name: pessoa_relacao pessoa_relacao_par_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa_relacao
    ADD CONSTRAINT pessoa_relacao_par_key UNIQUE (responsavel_id, voluntario_id);


--
-- Name: pessoa_relacao pessoa_relacao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa_relacao
    ADD CONSTRAINT pessoa_relacao_pkey PRIMARY KEY (id);


--
-- Name: pessoa_telefone pessoa_telefone_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa_telefone
    ADD CONSTRAINT pessoa_telefone_pkey PRIMARY KEY (id);


--
-- Name: pessoa pessoa_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa
    ADD CONSTRAINT pessoa_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: refresh_token refresh_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_pkey PRIMARY KEY (id);


--
-- Name: refresh_token refresh_token_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_token_hash_key UNIQUE (token_hash);


--
-- Name: resposta_indisponibilidade resposta_indisponibilidade_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resposta_indisponibilidade
    ADD CONSTRAINT resposta_indisponibilidade_pkey PRIMARY KEY (id);


--
-- Name: suporte_codigo suporte_codigo_hash_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suporte_codigo
    ADD CONSTRAINT suporte_codigo_hash_codigo_key UNIQUE (hash_codigo);


--
-- Name: suporte_codigo suporte_codigo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suporte_codigo
    ADD CONSTRAINT suporte_codigo_pkey PRIMARY KEY (id);


--
-- Name: tarefa tarefa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarefa
    ADD CONSTRAINT tarefa_pkey PRIMARY KEY (id);


--
-- Name: tenant tenant_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_codigo_key UNIQUE (codigo);


--
-- Name: tenant_email tenant_email_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_email
    ADD CONSTRAINT tenant_email_pkey PRIMARY KEY (id);


--
-- Name: tenant tenant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_pkey PRIMARY KEY (id);


--
-- Name: tenant_sequencial tenant_sequencial_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_sequencial
    ADD CONSTRAINT tenant_sequencial_pkey PRIMARY KEY (tenant_id, tabela);


--
-- Name: tenant tenant_slug_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_slug_key UNIQUE (slug);


--
-- Name: tenant_telefone tenant_telefone_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_telefone
    ADD CONSTRAINT tenant_telefone_pkey PRIMARY KEY (id);


--
-- Name: escalas uq_escalas_sequencial; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escalas
    ADD CONSTRAINT uq_escalas_sequencial UNIQUE (tenant_id, sequencial);


--
-- Name: inscricoes uq_inscricoes_sequencial; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricoes
    ADD CONSTRAINT uq_inscricoes_sequencial UNIQUE (tenant_id, sequencial);


--
-- Name: perfil uq_perfil_sequencial; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil
    ADD CONSTRAINT uq_perfil_sequencial UNIQUE (tenant_id, sequencial);


--
-- Name: pessoa uq_pessoa_sequencial; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa
    ADD CONSTRAINT uq_pessoa_sequencial UNIQUE (tenant_id, sequencial);


--
-- Name: usuario_tenant uq_usuario_tenant_sequencial; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_tenant
    ADD CONSTRAINT uq_usuario_tenant_sequencial UNIQUE (tenant_id, sequencial);


--
-- Name: usuario usuario_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_email_key UNIQUE (email);


--
-- Name: usuario usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (id);


--
-- Name: usuario_tenant usuario_tenant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_tenant
    ADD CONSTRAINT usuario_tenant_pkey PRIMARY KEY (usuario_id, tenant_id);


--
-- Name: resposta_indisponibilidade ux_resposta_voluntario_mes; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resposta_indisponibilidade
    ADD CONSTRAINT ux_resposta_voluntario_mes UNIQUE (tenant_id, voluntario_id, ano, mes);


--
-- Name: voluntarios voluntarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voluntarios
    ADD CONSTRAINT voluntarios_pkey PRIMARY KEY (id);


--
-- Name: voluntarios voluntarios_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voluntarios
    ADD CONSTRAINT voluntarios_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: diocese_nome_lower_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX diocese_nome_lower_key ON public.diocese USING btree (lower(nome));


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_anexo_comunicado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_anexo_comunicado ON public.comunicado_anexo USING btree (comunicado_id);


--
-- Name: idx_audit_log_tenant_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_tenant_created_at ON public.audit_log USING btree (tenant_id, created_at DESC);


--
-- Name: idx_audit_log_tenant_entidade; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_tenant_entidade ON public.audit_log USING btree (tenant_id, entidade, entidade_id);


--
-- Name: idx_comunicado_tenant_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comunicado_tenant_created ON public.comunicado USING btree (tenant_id, created_at DESC);


--
-- Name: idx_destinatario_comunicado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_destinatario_comunicado ON public.comunicado_destinatario USING btree (comunicado_id);


--
-- Name: idx_destinatario_cota_mes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_destinatario_cota_mes ON public.comunicado_destinatario USING btree (tenant_id, cota_competencia, comunicado_id) WHERE (cota_competencia IS NOT NULL);


--
-- Name: idx_destinatario_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_destinatario_tenant_status ON public.comunicado_destinatario USING btree (tenant_id, status);


--
-- Name: idx_disponibilidade_voluntario_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_disponibilidade_voluntario_tenant ON public.disponibilidade_voluntario USING btree (tenant_id);


--
-- Name: idx_escala_eventos_tenant_escala; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_escala_eventos_tenant_escala ON public.escala_eventos USING btree (tenant_id, escala_id);


--
-- Name: idx_escala_eventos_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_escala_eventos_tenant_id ON public.escala_eventos USING btree (tenant_id);


--
-- Name: idx_escala_vagas_tenant_evento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_escala_vagas_tenant_evento ON public.escala_vagas USING btree (tenant_id, evento_id);


--
-- Name: idx_escala_vagas_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_escala_vagas_tenant_id ON public.escala_vagas USING btree (tenant_id);


--
-- Name: idx_escala_vagas_tenant_voluntario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_escala_vagas_tenant_voluntario ON public.escala_vagas USING btree (tenant_id, voluntario_id);


--
-- Name: idx_escalas_ano_mes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_escalas_ano_mes ON public.escalas USING btree (ano, mes);


--
-- Name: idx_escalas_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_escalas_status ON public.escalas USING btree (status);


--
-- Name: idx_escalas_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_escalas_tenant_id ON public.escalas USING btree (tenant_id);


--
-- Name: idx_evento_foto_evento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evento_foto_evento ON public.evento_foto USING btree (evento_id);


--
-- Name: idx_evento_inscricao_evento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evento_inscricao_evento ON public.evento_inscricao USING btree (evento_id);


--
-- Name: idx_evento_tenant_inicio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evento_tenant_inicio ON public.evento USING btree (tenant_id, inicio);


--
-- Name: idx_indisponibilidade_tenant_data; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_indisponibilidade_tenant_data ON public.indisponibilidade_voluntario USING btree (tenant_id, data);


--
-- Name: idx_inscricao_responsaveis_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inscricao_responsaveis_tenant_id ON public.inscricao_responsaveis USING btree (tenant_id);


--
-- Name: idx_inscricoes_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inscricoes_tenant_id ON public.inscricoes USING btree (tenant_id);


--
-- Name: idx_inscricoes_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inscricoes_tenant_status ON public.inscricoes USING btree (tenant_id, status);


--
-- Name: idx_integracao_nonce_recebido; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_integracao_nonce_recebido ON public.integracao_nonce USING btree (recebido_em);


--
-- Name: idx_layout_envio_tenant_nome; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_layout_envio_tenant_nome ON public.layout_envio USING btree (tenant_id, lower((nome)::text));


--
-- Name: idx_password_reset_token_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_reset_token_expires_at ON public.password_reset_token USING btree (expires_at);


--
-- Name: idx_password_reset_token_usuario_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_reset_token_usuario_id ON public.password_reset_token USING btree (usuario_id);


--
-- Name: idx_perfil_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_perfil_tenant ON public.perfil USING btree (tenant_id);


--
-- Name: idx_pessoa_email_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pessoa_email_lookup ON public.pessoa_email USING btree (tenant_id, lower(email)) WHERE (principal = true);


--
-- Name: idx_pessoa_email_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pessoa_email_tenant ON public.pessoa_email USING btree (tenant_id);


--
-- Name: idx_pessoa_nome_fts; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pessoa_nome_fts ON public.pessoa USING gin (to_tsvector('portuguese'::regconfig, nome_completo));


--
-- Name: idx_pessoa_relacao_responsavel; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pessoa_relacao_responsavel ON public.pessoa_relacao USING btree (responsavel_id);


--
-- Name: idx_pessoa_relacao_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pessoa_relacao_tenant ON public.pessoa_relacao USING btree (tenant_id);


--
-- Name: idx_pessoa_relacao_voluntario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pessoa_relacao_voluntario ON public.pessoa_relacao USING btree (voluntario_id);


--
-- Name: idx_pessoa_telefone_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pessoa_telefone_tenant ON public.pessoa_telefone USING btree (tenant_id);


--
-- Name: idx_pessoa_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pessoa_tenant_id ON public.pessoa USING btree (tenant_id);


--
-- Name: idx_pessoa_tenant_responsavel; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pessoa_tenant_responsavel ON public.pessoa USING btree (tenant_id) WHERE e_responsavel;


--
-- Name: idx_pessoa_tenant_voluntario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pessoa_tenant_voluntario ON public.pessoa USING btree (tenant_id) WHERE e_voluntario;


--
-- Name: idx_refresh_token_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_token_expires_at ON public.refresh_token USING btree (expires_at);


--
-- Name: idx_refresh_token_usuario_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_token_usuario_id ON public.refresh_token USING btree (usuario_id);


--
-- Name: idx_tenant_diocese; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_diocese ON public.tenant USING btree (diocese_id);


--
-- Name: idx_tenant_email_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_email_tenant ON public.tenant_email USING btree (tenant_id);


--
-- Name: idx_tenant_telefone_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_telefone_tenant ON public.tenant_telefone USING btree (tenant_id);


--
-- Name: idx_usuario_tenant_perfil; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuario_tenant_perfil ON public.usuario_tenant USING btree (perfil_id);


--
-- Name: idx_usuario_tenant_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuario_tenant_tenant_id ON public.usuario_tenant USING btree (tenant_id);


--
-- Name: idx_voluntarios_ativo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_voluntarios_ativo ON public.voluntarios USING btree (ativo);


--
-- Name: idx_voluntarios_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_voluntarios_tenant_id ON public.voluntarios USING btree (tenant_id);


--
-- Name: idx_voluntarios_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_voluntarios_tipo ON public.voluntarios USING btree (tipo);


--
-- Name: ix_candidatura_escala; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_candidatura_escala ON public.escala_candidatura USING btree (tenant_id, escala_id, criada_em, id);


--
-- Name: ix_candidatura_pessoal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_candidatura_pessoal ON public.escala_candidatura USING btree (tenant_id, pessoa_id, criada_em, id);


--
-- Name: ix_candidatura_vaga; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_candidatura_vaga ON public.escala_candidatura USING btree (tenant_id, vaga_id, situacao);


--
-- Name: ix_destinatario_pronto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_destinatario_pronto ON public.comunicado_destinatario USING btree (tenant_id, proxima_tentativa, id) WHERE ((status)::text = 'PENDENTE'::text);


--
-- Name: ix_financeiro_movimento_baixa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_financeiro_movimento_baixa ON public.financeiro_movimento USING btree (tenant_id, data_pagamento, conta_id) WHERE ((situacao)::text = 'PAGO'::text);


--
-- Name: ix_financeiro_movimento_vencimento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_financeiro_movimento_vencimento ON public.financeiro_movimento USING btree (tenant_id, vencimento, id);


--
-- Name: ix_importacao_pessoa_mes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_importacao_pessoa_mes ON public.importacao_pessoa USING btree (tenant_id, competencia);


--
-- Name: ix_mural_aviso_lista; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_mural_aviso_lista ON public.mural_aviso USING btree (tenant_id, status, criado_em DESC, id DESC);


--
-- Name: ix_mural_aviso_recentes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_mural_aviso_recentes ON public.mural_aviso USING btree (tenant_id, criado_em DESC, id DESC);


--
-- Name: ix_pastoral_lista; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_pastoral_lista ON public.pastoral_equipe USING btree (tenant_id, nome, id);


--
-- Name: ix_pastoral_membros; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_pastoral_membros ON public.pastoral_membro USING btree (tenant_id, equipe_id, id);


--
-- Name: ix_relatorio_evento_data; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_relatorio_evento_data ON public.escala_eventos USING btree (tenant_id, data, id) WHERE (referencia = false);


--
-- Name: ix_resposta_historico; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_resposta_historico ON public.escala_resposta_historico USING btree (tenant_id, vaga_id, pessoa_id, respondido_em, id);


--
-- Name: ix_tarefa_lista; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_tarefa_lista ON public.tarefa USING btree (tenant_id, status, criado_em DESC, id DESC);


--
-- Name: ix_tarefa_recentes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_tarefa_recentes ON public.tarefa USING btree (tenant_id, criado_em DESC, id DESC);


--
-- Name: ix_tarefa_responsavel; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_tarefa_responsavel ON public.tarefa USING btree (tenant_id, responsavel_usuario_id, status, criado_em, id);


--
-- Name: ix_troca_escala; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_troca_escala ON public.escala_troca USING btree (tenant_id, escala_id, criada_em, id);


--
-- Name: ix_troca_solicitante; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_troca_solicitante ON public.escala_troca USING btree (tenant_id, solicitante_id, criada_em, id);


--
-- Name: ix_troca_substituto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_troca_substituto ON public.escala_troca USING btree (tenant_id, substituto_id, criada_em, id);


--
-- Name: uq_calendario_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_calendario_usuario ON public.calendario_assinatura USING btree (tenant_id, usuario_id);


--
-- Name: uq_inscricao_email_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_inscricao_email_principal ON public.inscricao_email USING btree (inscricao_id) WHERE (principal = true);


--
-- Name: uq_inscricao_responsavel_email_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_inscricao_responsavel_email_principal ON public.inscricao_responsavel_email USING btree (inscricao_responsavel_id) WHERE (principal = true);


--
-- Name: uq_inscricao_responsavel_telefone_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_inscricao_responsavel_telefone_principal ON public.inscricao_responsavel_telefone USING btree (inscricao_responsavel_id) WHERE (principal = true);


--
-- Name: uq_inscricao_telefone_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_inscricao_telefone_principal ON public.inscricao_telefone USING btree (inscricao_id) WHERE (principal = true);


--
-- Name: uq_layout_escala_nome_tenant_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_layout_escala_nome_tenant_tipo ON public.layout_escala USING btree (tenant_id, tipo, lower((nome)::text));


--
-- Name: uq_layout_escala_padrao_tenant_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_layout_escala_padrao_tenant_tipo ON public.layout_escala USING btree (tenant_id, tipo) WHERE padrao;


--
-- Name: uq_pessoa_email_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_pessoa_email_principal ON public.pessoa_email USING btree (pessoa_id) WHERE (principal = true);


--
-- Name: uq_pessoa_relacao_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_pessoa_relacao_principal ON public.pessoa_relacao USING btree (voluntario_id) WHERE (principal = true);


--
-- Name: uq_pessoa_telefone_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_pessoa_telefone_principal ON public.pessoa_telefone USING btree (pessoa_id) WHERE (principal = true);


--
-- Name: uq_tenant_email_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_tenant_email_principal ON public.tenant_email USING btree (tenant_id) WHERE (principal = true);


--
-- Name: uq_tenant_telefone_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_tenant_telefone_principal ON public.tenant_telefone USING btree (tenant_id) WHERE (principal = true);


--
-- Name: uq_usuario_pessoa_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_usuario_pessoa_tenant ON public.usuario_tenant USING btree (tenant_id, pessoa_id) WHERE (pessoa_id IS NOT NULL);


--
-- Name: uq_voluntario_por_evento; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_voluntario_por_evento ON public.escala_vagas USING btree (evento_id, voluntario_id) WHERE (voluntario_id IS NOT NULL);


--
-- Name: ux_disponibilidade_pontual; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_disponibilidade_pontual ON public.disponibilidade_voluntario USING btree (voluntario_id, data, periodo) WHERE (data IS NOT NULL);


--
-- Name: ux_disponibilidade_recorrente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_disponibilidade_recorrente ON public.disponibilidade_voluntario USING btree (voluntario_id, dia_semana, periodo) WHERE (dia_semana IS NOT NULL);


--
-- Name: ux_financeiro_categoria_nome; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_financeiro_categoria_nome ON public.financeiro_categoria USING btree (tenant_id, lower((nome)::text));


--
-- Name: ux_financeiro_conta_nome; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_financeiro_conta_nome ON public.financeiro_conta USING btree (tenant_id, lower((nome)::text));


--
-- Name: ux_indisponibilidade_dia_inteiro; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_indisponibilidade_dia_inteiro ON public.indisponibilidade_voluntario USING btree (tenant_id, voluntario_id, data) WHERE (periodo IS NULL);


--
-- Name: ux_indisponibilidade_periodo; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_indisponibilidade_periodo ON public.indisponibilidade_voluntario USING btree (tenant_id, voluntario_id, data, periodo) WHERE (periodo IS NOT NULL);


--
-- Name: ux_inscricao_responsavel_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_inscricao_responsavel_principal ON public.inscricao_responsaveis USING btree (inscricao_id) WHERE (principal = true);


--
-- Name: ux_pessoa_tenant_cpf; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pessoa_tenant_cpf ON public.pessoa USING btree (tenant_id, cpf) WHERE ((cpf IS NOT NULL) AND (cpf <> ''::text));


--
-- Name: ux_troca_ativa_vaga; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_troca_ativa_vaga ON public.escala_troca USING btree (tenant_id, vaga_id) WHERE ((situacao)::text = ANY ((ARRAY['AGUARDANDO_ACEITE'::character varying, 'ACEITA'::character varying])::text[]));


--
-- Name: diocese trg_diocese_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_diocese_updated_at BEFORE UPDATE ON public.diocese FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: escalas trg_escalas_sequencial; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_escalas_sequencial BEFORE INSERT ON public.escalas FOR EACH ROW EXECUTE FUNCTION public.proximo_sequencial();


--
-- Name: escalas trg_escalas_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_escalas_updated_at BEFORE UPDATE ON public.escalas FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: inscricao_responsaveis trg_inscricao_responsaveis_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_inscricao_responsaveis_updated_at BEFORE UPDATE ON public.inscricao_responsaveis FOR EACH ROW EXECUTE FUNCTION private.set_updated_at();


--
-- Name: inscricoes trg_inscricoes_sequencial; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_inscricoes_sequencial BEFORE INSERT ON public.inscricoes FOR EACH ROW EXECUTE FUNCTION public.proximo_sequencial();


--
-- Name: inscricoes trg_inscricoes_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_inscricoes_updated_at BEFORE UPDATE ON public.inscricoes FOR EACH ROW EXECUTE FUNCTION private.set_updated_at();


--
-- Name: layout_envio trg_layout_envio_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_layout_envio_updated_at BEFORE UPDATE ON public.layout_envio FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: layout_escala trg_layout_escala_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_layout_escala_updated_at BEFORE UPDATE ON public.layout_escala FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: perfil trg_perfil_sequencial; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_perfil_sequencial BEFORE INSERT ON public.perfil FOR EACH ROW EXECUTE FUNCTION public.proximo_sequencial();


--
-- Name: perfil trg_perfil_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_perfil_updated_at BEFORE UPDATE ON public.perfil FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: pessoa trg_pessoa_sequencial; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_pessoa_sequencial BEFORE INSERT ON public.pessoa FOR EACH ROW EXECUTE FUNCTION public.proximo_sequencial();


--
-- Name: pessoa trg_pessoa_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_pessoa_updated_at BEFORE UPDATE ON public.pessoa FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: tenant trg_tenant_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_tenant_updated_at BEFORE UPDATE ON public.tenant FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: usuario_tenant trg_usuario_tenant_sequencial; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_usuario_tenant_sequencial BEFORE INSERT ON public.usuario_tenant FOR EACH ROW EXECUTE FUNCTION public.proximo_sequencial();


--
-- Name: usuario_tenant trg_usuario_tenant_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_usuario_tenant_updated_at BEFORE UPDATE ON public.usuario_tenant FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: usuario trg_usuario_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_usuario_updated_at BEFORE UPDATE ON public.usuario FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: voluntarios trg_voluntarios_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_voluntarios_updated_at BEFORE UPDATE ON public.voluntarios FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: audit_log audit_log_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: audit_log audit_log_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.usuario(id) ON DELETE SET NULL;


--
-- Name: calendario_assinatura calendario_assinatura_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calendario_assinatura
    ADD CONSTRAINT calendario_assinatura_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: calendario_assinatura calendario_assinatura_tenant_id_pessoa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calendario_assinatura
    ADD CONSTRAINT calendario_assinatura_tenant_id_pessoa_id_fkey FOREIGN KEY (tenant_id, pessoa_id) REFERENCES public.pessoa(tenant_id, id);


--
-- Name: calendario_assinatura calendario_assinatura_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calendario_assinatura
    ADD CONSTRAINT calendario_assinatura_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id);


--
-- Name: comunicado_anexo comunicado_anexo_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comunicado_anexo
    ADD CONSTRAINT comunicado_anexo_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: comunicado comunicado_criado_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comunicado
    ADD CONSTRAINT comunicado_criado_por_fkey FOREIGN KEY (criado_por) REFERENCES public.usuario(id) ON DELETE SET NULL;


--
-- Name: comunicado_destinatario comunicado_destinatario_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comunicado_destinatario
    ADD CONSTRAINT comunicado_destinatario_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: comunicado comunicado_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comunicado
    ADD CONSTRAINT comunicado_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: direitos_locais direitos_locais_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.direitos_locais
    ADD CONSTRAINT direitos_locais_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: disponibilidade_voluntario disponibilidade_voluntario_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disponibilidade_voluntario
    ADD CONSTRAINT disponibilidade_voluntario_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: disponibilidade_voluntario disponibilidade_voluntario_voluntario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disponibilidade_voluntario
    ADD CONSTRAINT disponibilidade_voluntario_voluntario_id_fkey FOREIGN KEY (voluntario_id) REFERENCES public.voluntarios(id) ON DELETE CASCADE;


--
-- Name: escala_candidatura escala_candidatura_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_candidatura
    ADD CONSTRAINT escala_candidatura_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: escala_eventos escala_eventos_tenant_escala_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_eventos
    ADD CONSTRAINT escala_eventos_tenant_escala_fkey FOREIGN KEY (tenant_id, escala_id) REFERENCES public.escalas(tenant_id, id) ON DELETE CASCADE;


--
-- Name: escala_eventos escala_eventos_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_eventos
    ADD CONSTRAINT escala_eventos_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: escala_resposta_historico escala_resposta_historico_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_resposta_historico
    ADD CONSTRAINT escala_resposta_historico_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: escala_troca escala_troca_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_troca
    ADD CONSTRAINT escala_troca_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: escala_vagas escala_vagas_tenant_evento_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_vagas
    ADD CONSTRAINT escala_vagas_tenant_evento_fkey FOREIGN KEY (tenant_id, evento_id) REFERENCES public.escala_eventos(tenant_id, id) ON DELETE CASCADE;


--
-- Name: escala_vagas escala_vagas_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_vagas
    ADD CONSTRAINT escala_vagas_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: escala_vagas escala_vagas_tenant_voluntario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_vagas
    ADD CONSTRAINT escala_vagas_tenant_voluntario_fkey FOREIGN KEY (tenant_id, voluntario_id) REFERENCES public.voluntarios(tenant_id, id) ON DELETE SET NULL (voluntario_id);


--
-- Name: escalas escalas_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escalas
    ADD CONSTRAINT escalas_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.usuario(id) ON DELETE SET NULL;


--
-- Name: escalas escalas_layout_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escalas
    ADD CONSTRAINT escalas_layout_id_fkey FOREIGN KEY (layout_id) REFERENCES public.layout_escala(id);


--
-- Name: escalas escalas_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escalas
    ADD CONSTRAINT escalas_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: evento_foto evento_foto_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_foto
    ADD CONSTRAINT evento_foto_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: evento_inscricao evento_inscricao_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_inscricao
    ADD CONSTRAINT evento_inscricao_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: evento evento_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento
    ADD CONSTRAINT evento_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: financeiro_categoria financeiro_categoria_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financeiro_categoria
    ADD CONSTRAINT financeiro_categoria_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: financeiro_conta financeiro_conta_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financeiro_conta
    ADD CONSTRAINT financeiro_conta_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: financeiro_movimento financeiro_movimento_tenant_id_categoria_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financeiro_movimento
    ADD CONSTRAINT financeiro_movimento_tenant_id_categoria_id_fkey FOREIGN KEY (tenant_id, categoria_id) REFERENCES public.financeiro_categoria(tenant_id, id);


--
-- Name: financeiro_movimento financeiro_movimento_tenant_id_conta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financeiro_movimento
    ADD CONSTRAINT financeiro_movimento_tenant_id_conta_id_fkey FOREIGN KEY (tenant_id, conta_id) REFERENCES public.financeiro_conta(tenant_id, id);


--
-- Name: financeiro_movimento financeiro_movimento_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financeiro_movimento
    ADD CONSTRAINT financeiro_movimento_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: comunicado_anexo fk_anexo_comunicado; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comunicado_anexo
    ADD CONSTRAINT fk_anexo_comunicado FOREIGN KEY (tenant_id, comunicado_id) REFERENCES public.comunicado(tenant_id, id) ON DELETE CASCADE;


--
-- Name: comunicado fk_comunicado_layout; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comunicado
    ADD CONSTRAINT fk_comunicado_layout FOREIGN KEY (tenant_id, layout_id) REFERENCES public.layout_envio(tenant_id, id) ON DELETE SET NULL (layout_id);


--
-- Name: comunicado_destinatario fk_destinatario_comunicado; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comunicado_destinatario
    ADD CONSTRAINT fk_destinatario_comunicado FOREIGN KEY (tenant_id, comunicado_id) REFERENCES public.comunicado(tenant_id, id) ON DELETE CASCADE;


--
-- Name: comunicado_destinatario fk_destinatario_pessoa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comunicado_destinatario
    ADD CONSTRAINT fk_destinatario_pessoa FOREIGN KEY (tenant_id, pessoa_id) REFERENCES public.pessoa(tenant_id, id) ON DELETE SET NULL (pessoa_id);


--
-- Name: evento fk_evento_email_confirmacao; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento
    ADD CONSTRAINT fk_evento_email_confirmacao FOREIGN KEY (tenant_id, email_layout_confirmacao_id) REFERENCES public.layout_envio(tenant_id, id) ON DELETE SET NULL (email_layout_confirmacao_id);


--
-- Name: evento fk_evento_email_lembrete; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento
    ADD CONSTRAINT fk_evento_email_lembrete FOREIGN KEY (tenant_id, email_layout_lembrete_id) REFERENCES public.layout_envio(tenant_id, id) ON DELETE SET NULL (email_layout_lembrete_id);


--
-- Name: evento_foto fk_evento_foto_evento; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_foto
    ADD CONSTRAINT fk_evento_foto_evento FOREIGN KEY (tenant_id, evento_id) REFERENCES public.evento(tenant_id, id) ON DELETE CASCADE;


--
-- Name: evento_inscricao fk_evento_inscricao_evento; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_inscricao
    ADD CONSTRAINT fk_evento_inscricao_evento FOREIGN KEY (tenant_id, evento_id) REFERENCES public.evento(tenant_id, id) ON DELETE CASCADE;


--
-- Name: evento_inscricao fk_evento_inscricao_pessoa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_inscricao
    ADD CONSTRAINT fk_evento_inscricao_pessoa FOREIGN KEY (tenant_id, pessoa_id) REFERENCES public.pessoa(tenant_id, id) ON DELETE CASCADE;


--
-- Name: evento fk_evento_whatsapp_confirmacao; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento
    ADD CONSTRAINT fk_evento_whatsapp_confirmacao FOREIGN KEY (tenant_id, whatsapp_layout_confirmacao_id) REFERENCES public.layout_envio(tenant_id, id) ON DELETE SET NULL (whatsapp_layout_confirmacao_id);


--
-- Name: evento fk_evento_whatsapp_lembrete; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento
    ADD CONSTRAINT fk_evento_whatsapp_lembrete FOREIGN KEY (tenant_id, whatsapp_layout_lembrete_id) REFERENCES public.layout_envio(tenant_id, id) ON DELETE SET NULL (whatsapp_layout_lembrete_id);


--
-- Name: indisponibilidade_voluntario fk_indisponibilidade_voluntario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indisponibilidade_voluntario
    ADD CONSTRAINT fk_indisponibilidade_voluntario FOREIGN KEY (tenant_id, voluntario_id) REFERENCES public.voluntarios(tenant_id, id) ON DELETE CASCADE;


--
-- Name: resposta_indisponibilidade fk_resposta_voluntario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resposta_indisponibilidade
    ADD CONSTRAINT fk_resposta_voluntario FOREIGN KEY (tenant_id, voluntario_id) REFERENCES public.voluntarios(tenant_id, id) ON DELETE CASCADE;


--
-- Name: tarefa fk_tarefa_responsavel; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarefa
    ADD CONSTRAINT fk_tarefa_responsavel FOREIGN KEY (responsavel_usuario_id, tenant_id) REFERENCES public.usuario_tenant(usuario_id, tenant_id) ON DELETE SET NULL (responsavel_usuario_id);


--
-- Name: usuario_tenant fk_usuario_pessoa_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_tenant
    ADD CONSTRAINT fk_usuario_pessoa_tenant FOREIGN KEY (tenant_id, pessoa_id) REFERENCES public.pessoa(tenant_id, id);


--
-- Name: importacao_pessoa importacao_pessoa_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.importacao_pessoa
    ADD CONSTRAINT importacao_pessoa_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: indisponibilidade_mes indisponibilidade_mes_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indisponibilidade_mes
    ADD CONSTRAINT indisponibilidade_mes_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: indisponibilidade_voluntario indisponibilidade_voluntario_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indisponibilidade_voluntario
    ADD CONSTRAINT indisponibilidade_voluntario_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: inscricao_email inscricao_email_inscricao_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_email
    ADD CONSTRAINT inscricao_email_inscricao_fkey FOREIGN KEY (tenant_id, inscricao_id) REFERENCES public.inscricoes(tenant_id, id) ON DELETE CASCADE;


--
-- Name: inscricao_email inscricao_email_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_email
    ADD CONSTRAINT inscricao_email_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: inscricao_responsaveis inscricao_responsaveis_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_responsaveis
    ADD CONSTRAINT inscricao_responsaveis_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: inscricao_responsaveis inscricao_responsaveis_tenant_inscricao_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_responsaveis
    ADD CONSTRAINT inscricao_responsaveis_tenant_inscricao_fkey FOREIGN KEY (tenant_id, inscricao_id) REFERENCES public.inscricoes(tenant_id, id) ON DELETE CASCADE;


--
-- Name: inscricao_responsavel_email inscricao_responsavel_email_inscricao_responsavel_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_responsavel_email
    ADD CONSTRAINT inscricao_responsavel_email_inscricao_responsavel_id_fkey FOREIGN KEY (inscricao_responsavel_id) REFERENCES public.inscricao_responsaveis(id) ON DELETE CASCADE;


--
-- Name: inscricao_responsavel_email inscricao_responsavel_email_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_responsavel_email
    ADD CONSTRAINT inscricao_responsavel_email_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: inscricao_responsavel_telefone inscricao_responsavel_telefone_inscricao_responsavel_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_responsavel_telefone
    ADD CONSTRAINT inscricao_responsavel_telefone_inscricao_responsavel_id_fkey FOREIGN KEY (inscricao_responsavel_id) REFERENCES public.inscricao_responsaveis(id) ON DELETE CASCADE;


--
-- Name: inscricao_responsavel_telefone inscricao_responsavel_telefone_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_responsavel_telefone
    ADD CONSTRAINT inscricao_responsavel_telefone_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: inscricao_telefone inscricao_telefone_inscricao_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_telefone
    ADD CONSTRAINT inscricao_telefone_inscricao_fkey FOREIGN KEY (tenant_id, inscricao_id) REFERENCES public.inscricoes(tenant_id, id) ON DELETE CASCADE;


--
-- Name: inscricao_telefone inscricao_telefone_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_telefone
    ADD CONSTRAINT inscricao_telefone_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: inscricoes inscricoes_aprovado_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricoes
    ADD CONSTRAINT inscricoes_aprovado_por_fkey FOREIGN KEY (aprovado_por) REFERENCES public.usuario(id);


--
-- Name: inscricoes inscricoes_rejeitado_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricoes
    ADD CONSTRAINT inscricoes_rejeitado_por_fkey FOREIGN KEY (rejeitado_por) REFERENCES public.usuario(id);


--
-- Name: inscricoes inscricoes_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricoes
    ADD CONSTRAINT inscricoes_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: inscricoes inscricoes_tenant_voluntario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricoes
    ADD CONSTRAINT inscricoes_tenant_voluntario_fkey FOREIGN KEY (tenant_id, voluntario_id) REFERENCES public.voluntarios(tenant_id, id) ON DELETE SET NULL (voluntario_id);


--
-- Name: layout_envio layout_envio_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.layout_envio
    ADD CONSTRAINT layout_envio_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: mural_aviso mural_aviso_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mural_aviso
    ADD CONSTRAINT mural_aviso_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: paroquia_whatsapp paroquia_whatsapp_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.paroquia_whatsapp
    ADD CONSTRAINT paroquia_whatsapp_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: password_reset_token password_reset_token_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_token
    ADD CONSTRAINT password_reset_token_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- Name: pastoral_equipe pastoral_equipe_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pastoral_equipe
    ADD CONSTRAINT pastoral_equipe_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: pastoral_membro pastoral_membro_tenant_id_equipe_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pastoral_membro
    ADD CONSTRAINT pastoral_membro_tenant_id_equipe_id_fkey FOREIGN KEY (tenant_id, equipe_id) REFERENCES public.pastoral_equipe(tenant_id, id);


--
-- Name: pastoral_membro pastoral_membro_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pastoral_membro
    ADD CONSTRAINT pastoral_membro_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: pastoral_membro pastoral_membro_tenant_id_pessoa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pastoral_membro
    ADD CONSTRAINT pastoral_membro_tenant_id_pessoa_id_fkey FOREIGN KEY (tenant_id, pessoa_id) REFERENCES public.pessoa(tenant_id, id);


--
-- Name: perfil_permissao perfil_permissao_perfil_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil_permissao
    ADD CONSTRAINT perfil_permissao_perfil_id_fkey FOREIGN KEY (perfil_id) REFERENCES public.perfil(id) ON DELETE CASCADE;


--
-- Name: perfil perfil_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil
    ADD CONSTRAINT perfil_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: pessoa_email pessoa_email_pessoa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa_email
    ADD CONSTRAINT pessoa_email_pessoa_fkey FOREIGN KEY (tenant_id, pessoa_id) REFERENCES public.pessoa(tenant_id, id) ON DELETE CASCADE;


--
-- Name: pessoa_email pessoa_email_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa_email
    ADD CONSTRAINT pessoa_email_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: pessoa_relacao pessoa_relacao_responsavel_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa_relacao
    ADD CONSTRAINT pessoa_relacao_responsavel_fkey FOREIGN KEY (tenant_id, responsavel_id) REFERENCES public.pessoa(tenant_id, id) ON DELETE CASCADE;


--
-- Name: pessoa_relacao pessoa_relacao_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa_relacao
    ADD CONSTRAINT pessoa_relacao_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: pessoa_relacao pessoa_relacao_voluntario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa_relacao
    ADD CONSTRAINT pessoa_relacao_voluntario_fkey FOREIGN KEY (tenant_id, voluntario_id) REFERENCES public.pessoa(tenant_id, id) ON DELETE CASCADE;


--
-- Name: pessoa_telefone pessoa_telefone_pessoa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa_telefone
    ADD CONSTRAINT pessoa_telefone_pessoa_fkey FOREIGN KEY (tenant_id, pessoa_id) REFERENCES public.pessoa(tenant_id, id) ON DELETE CASCADE;


--
-- Name: pessoa_telefone pessoa_telefone_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa_telefone
    ADD CONSTRAINT pessoa_telefone_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: pessoa pessoa_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa
    ADD CONSTRAINT pessoa_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: refresh_token refresh_token_replaced_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_replaced_by_fkey FOREIGN KEY (replaced_by) REFERENCES public.refresh_token(id) ON DELETE SET NULL;


--
-- Name: refresh_token refresh_token_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- Name: resposta_indisponibilidade resposta_indisponibilidade_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resposta_indisponibilidade
    ADD CONSTRAINT resposta_indisponibilidade_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: suporte_codigo suporte_codigo_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suporte_codigo
    ADD CONSTRAINT suporte_codigo_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: tarefa tarefa_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarefa
    ADD CONSTRAINT tarefa_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: tenant tenant_diocese_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_diocese_id_fkey FOREIGN KEY (diocese_id) REFERENCES public.diocese(id);


--
-- Name: tenant_email tenant_email_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_email
    ADD CONSTRAINT tenant_email_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: tenant_sequencial tenant_sequencial_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_sequencial
    ADD CONSTRAINT tenant_sequencial_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: tenant_telefone tenant_telefone_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_telefone
    ADD CONSTRAINT tenant_telefone_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: usuario_tenant usuario_tenant_perfil_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_tenant
    ADD CONSTRAINT usuario_tenant_perfil_id_fkey FOREIGN KEY (perfil_id) REFERENCES public.perfil(id);


--
-- Name: usuario_tenant usuario_tenant_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_tenant
    ADD CONSTRAINT usuario_tenant_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: usuario_tenant usuario_tenant_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_tenant
    ADD CONSTRAINT usuario_tenant_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- Name: voluntarios voluntarios_pessoa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voluntarios
    ADD CONSTRAINT voluntarios_pessoa_fkey FOREIGN KEY (tenant_id, id) REFERENCES public.pessoa(tenant_id, id);


--
-- Name: voluntarios voluntarios_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voluntarios
    ADD CONSTRAINT voluntarios_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: audit_log; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.audit_log ENABLE ROW LEVEL SECURITY;

--
-- Name: calendario_assinatura; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.calendario_assinatura ENABLE ROW LEVEL SECURITY;

--
-- Name: comunicado; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.comunicado ENABLE ROW LEVEL SECURITY;

--
-- Name: comunicado_anexo; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.comunicado_anexo ENABLE ROW LEVEL SECURITY;

--
-- Name: comunicado_destinatario; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.comunicado_destinatario ENABLE ROW LEVEL SECURITY;

--
-- Name: diocese; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.diocese ENABLE ROW LEVEL SECURITY;

--
-- Name: direitos_locais; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.direitos_locais ENABLE ROW LEVEL SECURITY;

--
-- Name: disponibilidade_voluntario; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.disponibilidade_voluntario ENABLE ROW LEVEL SECURITY;

--
-- Name: escala_candidatura; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.escala_candidatura ENABLE ROW LEVEL SECURITY;

--
-- Name: escala_eventos; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.escala_eventos ENABLE ROW LEVEL SECURITY;

--
-- Name: escala_resposta_historico; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.escala_resposta_historico ENABLE ROW LEVEL SECURITY;

--
-- Name: escala_troca; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.escala_troca ENABLE ROW LEVEL SECURITY;

--
-- Name: escala_vagas; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.escala_vagas ENABLE ROW LEVEL SECURITY;

--
-- Name: escalas; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.escalas ENABLE ROW LEVEL SECURITY;

--
-- Name: evento; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.evento ENABLE ROW LEVEL SECURITY;

--
-- Name: evento_foto; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.evento_foto ENABLE ROW LEVEL SECURITY;

--
-- Name: evento_inscricao; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.evento_inscricao ENABLE ROW LEVEL SECURITY;

--
-- Name: fila_envio_janela; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.fila_envio_janela ENABLE ROW LEVEL SECURITY;

--
-- Name: financeiro_categoria; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.financeiro_categoria ENABLE ROW LEVEL SECURITY;

--
-- Name: financeiro_conta; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.financeiro_conta ENABLE ROW LEVEL SECURITY;

--
-- Name: financeiro_movimento; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.financeiro_movimento ENABLE ROW LEVEL SECURITY;

--
-- Name: importacao_pessoa; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.importacao_pessoa ENABLE ROW LEVEL SECURITY;

--
-- Name: indisponibilidade_mes; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.indisponibilidade_mes ENABLE ROW LEVEL SECURITY;

--
-- Name: indisponibilidade_voluntario; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.indisponibilidade_voluntario ENABLE ROW LEVEL SECURITY;

--
-- Name: inscricao_email; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.inscricao_email ENABLE ROW LEVEL SECURITY;

--
-- Name: inscricao_responsaveis; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.inscricao_responsaveis ENABLE ROW LEVEL SECURITY;

--
-- Name: inscricao_responsavel_email; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.inscricao_responsavel_email ENABLE ROW LEVEL SECURITY;

--
-- Name: inscricao_responsavel_telefone; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.inscricao_responsavel_telefone ENABLE ROW LEVEL SECURITY;

--
-- Name: inscricao_telefone; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.inscricao_telefone ENABLE ROW LEVEL SECURITY;

--
-- Name: inscricoes; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.inscricoes ENABLE ROW LEVEL SECURITY;

--
-- Name: integracao_nonce; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.integracao_nonce ENABLE ROW LEVEL SECURITY;

--
-- Name: integracao_operacao; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.integracao_operacao ENABLE ROW LEVEL SECURITY;

--
-- Name: layout_envio; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.layout_envio ENABLE ROW LEVEL SECURITY;

--
-- Name: layout_escala; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.layout_escala ENABLE ROW LEVEL SECURITY;

--
-- Name: mural_aviso; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.mural_aviso ENABLE ROW LEVEL SECURITY;

--
-- Name: paroquia_whatsapp; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.paroquia_whatsapp ENABLE ROW LEVEL SECURITY;

--
-- Name: password_reset_token; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.password_reset_token ENABLE ROW LEVEL SECURITY;

--
-- Name: pastoral_equipe; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.pastoral_equipe ENABLE ROW LEVEL SECURITY;

--
-- Name: pastoral_membro; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.pastoral_membro ENABLE ROW LEVEL SECURITY;

--
-- Name: perfil; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.perfil ENABLE ROW LEVEL SECURITY;

--
-- Name: perfil_permissao; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.perfil_permissao ENABLE ROW LEVEL SECURITY;

--
-- Name: pessoa; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.pessoa ENABLE ROW LEVEL SECURITY;

--
-- Name: pessoa_email; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.pessoa_email ENABLE ROW LEVEL SECURITY;

--
-- Name: pessoa_relacao; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.pessoa_relacao ENABLE ROW LEVEL SECURITY;

--
-- Name: pessoa_telefone; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.pessoa_telefone ENABLE ROW LEVEL SECURITY;

--
-- Name: refresh_token; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.refresh_token ENABLE ROW LEVEL SECURITY;

--
-- Name: resposta_indisponibilidade; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.resposta_indisponibilidade ENABLE ROW LEVEL SECURITY;

--
-- Name: suporte_codigo; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.suporte_codigo ENABLE ROW LEVEL SECURITY;

--
-- Name: tarefa; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.tarefa ENABLE ROW LEVEL SECURITY;

--
-- Name: tenant; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.tenant ENABLE ROW LEVEL SECURITY;

--
-- Name: tenant_email; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.tenant_email ENABLE ROW LEVEL SECURITY;

--
-- Name: tenant_sequencial; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.tenant_sequencial ENABLE ROW LEVEL SECURITY;

--
-- Name: tenant_telefone; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.tenant_telefone ENABLE ROW LEVEL SECURITY;

--
-- Name: usuario; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.usuario ENABLE ROW LEVEL SECURITY;

--
-- Name: usuario_tenant; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.usuario_tenant ENABLE ROW LEVEL SECURITY;

--
-- Name: voluntarios; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.voluntarios ENABLE ROW LEVEL SECURITY;

--
--

\unrestrict CpkY1bsg7NRcGj8o6gpceP3NAgdKMU5jLbka7HeWnwnZzaUBg1gXpd2o9YvFZKX

