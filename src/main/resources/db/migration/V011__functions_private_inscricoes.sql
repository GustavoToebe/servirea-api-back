-- V011__functions_private_inscricoes.sql
-- Fonte: extraído AO VIVO do painel do Supabase em 21/09/2026 (documento
-- técnico, seção 20.3). Este é o único bloco de lógica de negócio deste
-- baseline copiado literalmente (não reconstruído por inferência) — as
-- três funções abaixo são coladas exatamente como estavam em produção.

CREATE OR REPLACE FUNCTION private.criar_inscricao_impl(p_dados jsonb, p_responsaveis jsonb)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_inscricao_id uuid;
    v_funcoes public.funcao_escala[];
    v_principais integer;
BEGIN
    IF p_dados IS NULL OR jsonb_typeof(p_dados) <> 'object' THEN
        RAISE EXCEPTION 'Dados da inscrição inválidos';
    END IF;
    IF COALESCE(btrim(p_dados ->> 'nome_completo'), '') = '' THEN
        RAISE EXCEPTION 'Nome completo é obrigatório';
    END IF;

    IF p_responsaveis IS NULL OR jsonb_typeof(p_responsaveis) <> 'array' OR jsonb_array_length(p_responsaveis) = 0 THEN
        RAISE EXCEPTION 'Informe pelo menos um responsável';
    END IF;

    SELECT count(*) INTO v_principais
    FROM jsonb_to_recordset(p_responsaveis) AS r(principal boolean)
    WHERE COALESCE(r.principal, false) = true;
    IF v_principais <> 1 THEN
        RAISE EXCEPTION 'Informe exatamente um responsável principal';
    END IF;

    IF EXISTS (
        SELECT 1 FROM jsonb_to_recordset(p_responsaveis) AS r(parentesco text, nome text)
        WHERE COALESCE(btrim(r.nome), '') = '' OR COALESCE(btrim(r.parentesco), '') = ''
    ) THEN
        RAISE EXCEPTION 'Nome e parentesco são obrigatórios para todos os responsáveis';
    END IF;

    SELECT COALESCE(array_agg(t.valor::public.funcao_escala), '{}'::public.funcao_escala[])
    INTO v_funcoes
    FROM jsonb_array_elements_text(COALESCE(p_dados -> 'funcoes_habilitadas', '[]'::jsonb)) AS t(valor);

    -- Permite que a Edge Function defina o UUID antes do upload da foto.
    v_inscricao_id := COALESCE(NULLIF(p_dados ->> 'id', '')::uuid, gen_random_uuid());

    INSERT INTO public.inscricoes (
        id, nome_completo, data_nascimento, tipo, foto_path, etapa_catequese,
        eucaristia_ano, crisma_ano, rua, numero, bairro, telefone, celular, email,
        horario_estudo, observacoes, autoriza_whatsapp, funcoes_habilitadas, status
    ) VALUES (
        v_inscricao_id,
        btrim(p_dados ->> 'nome_completo'),
        NULLIF(p_dados ->> 'data_nascimento', '')::date,
        COALESCE(NULLIF(p_dados ->> 'tipo', '')::public.tipo_voluntario, 'COROINHA'::public.tipo_voluntario),
        NULLIF(p_dados ->> 'foto_path', ''),
        NULLIF(p_dados ->> 'etapa_catequese', ''),
        NULLIF(p_dados ->> 'eucaristia_ano', ''),
        NULLIF(p_dados ->> 'crisma_ano', ''),
        NULLIF(p_dados ->> 'rua', ''),
        NULLIF(p_dados ->> 'numero', ''),
        NULLIF(p_dados ->> 'bairro', ''),
        NULLIF(p_dados ->> 'telefone', ''),
        NULLIF(p_dados ->> 'celular', ''),
        NULLIF(p_dados ->> 'email', ''),
        NULLIF(p_dados ->> 'horario_estudo', ''),
        NULLIF(p_dados ->> 'observacoes', ''),
        COALESCE((p_dados ->> 'autoriza_whatsapp')::boolean, false),
        v_funcoes,
        'PENDENTE'::public.status_inscricao
    );

    INSERT INTO public.inscricao_responsaveis (
        inscricao_id, parentesco, nome, telefone, celular, email, principal
    )
    SELECT
        v_inscricao_id, btrim(r.parentesco), btrim(r.nome),
        NULLIF(btrim(r.telefone), ''), NULLIF(btrim(r.celular), ''), NULLIF(btrim(r.email), ''),
        COALESCE(r.principal, false)
    FROM jsonb_to_recordset(p_responsaveis) AS r(
        parentesco text, nome text, telefone text, celular text, email text, principal boolean
    );

    RETURN v_inscricao_id;
END;
$function$;

CREATE OR REPLACE FUNCTION private.aprovar_inscricao_impl(p_inscricao_id uuid)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_inscricao public.inscricoes%ROWTYPE;
    v_voluntario_id uuid;
    v_total_responsaveis integer;
    v_total_principais integer;
    v_usuario uuid;
BEGIN
    v_usuario := auth.uid();
    IF v_usuario IS NULL THEN
        RAISE EXCEPTION 'Usuário não autenticado';
    END IF;

    -- Bloqueia o registro durante a aprovação.
    SELECT * INTO v_inscricao FROM public.inscricoes WHERE id = p_inscricao_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Inscrição não encontrada';
    END IF;
    IF v_inscricao.status <> 'PENDENTE'::public.status_inscricao THEN
        RAISE EXCEPTION 'Somente inscrições pendentes podem ser aprovadas';
    END IF;

    SELECT count(*) INTO v_total_responsaveis
    FROM public.inscricao_responsaveis WHERE inscricao_id = p_inscricao_id;
    IF v_total_responsaveis = 0 THEN
        RAISE EXCEPTION 'A inscrição não possui responsáveis';
    END IF;

    SELECT count(*) INTO v_total_principais
    FROM public.inscricao_responsaveis WHERE inscricao_id = p_inscricao_id AND principal = true;
    IF v_total_principais <> 1 THEN
        RAISE EXCEPTION 'A inscrição deve possuir exatamente um responsável principal';
    END IF;

    INSERT INTO public.voluntarios (
        nome_completo, data_nascimento, tipo, ativo, foto_path, etapa_catequese,
        eucaristia_ano, crisma_ano, rua, numero, bairro, telefone, celular, email,
        horario_estudo, observacoes, autoriza_whatsapp, funcoes_habilitadas
    ) VALUES (
        v_inscricao.nome_completo, v_inscricao.data_nascimento, v_inscricao.tipo, true,
        v_inscricao.foto_path, v_inscricao.etapa_catequese, v_inscricao.eucaristia_ano,
        v_inscricao.crisma_ano, v_inscricao.rua, v_inscricao.numero, v_inscricao.bairro,
        v_inscricao.telefone, v_inscricao.celular, v_inscricao.email, v_inscricao.horario_estudo,
        v_inscricao.observacoes, v_inscricao.autoriza_whatsapp, v_inscricao.funcoes_habilitadas
    )
    RETURNING id INTO v_voluntario_id;

    INSERT INTO public.responsaveis (
        voluntario_id, parentesco, nome, telefone, celular, email, principal
    )
    SELECT v_voluntario_id, parentesco, nome, telefone, celular, email, principal
    FROM public.inscricao_responsaveis WHERE inscricao_id = p_inscricao_id;

    UPDATE public.inscricoes
    SET status = 'APROVADA'::public.status_inscricao,
        voluntario_id = v_voluntario_id,
        data_aprovacao = now(),
        aprovado_por = v_usuario
    WHERE id = p_inscricao_id;

    RETURN v_voluntario_id;
END;
$function$;

CREATE OR REPLACE FUNCTION private.rejeitar_inscricao_impl(p_inscricao_id uuid, p_motivo text DEFAULT NULL::text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_status public.status_inscricao;
    v_usuario uuid;
BEGIN
    v_usuario := auth.uid();
    IF v_usuario IS NULL THEN
        RAISE EXCEPTION 'Usuário não autenticado';
    END IF;

    SELECT status INTO v_status FROM public.inscricoes WHERE id = p_inscricao_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Inscrição não encontrada';
    END IF;
    IF v_status <> 'PENDENTE'::public.status_inscricao THEN
        RAISE EXCEPTION 'Somente inscrições pendentes podem ser rejeitadas';
    END IF;

    UPDATE public.inscricoes
    SET status = 'REJEITADA'::public.status_inscricao,
        data_rejeicao = now(),
        rejeitado_por = v_usuario,
        motivo_rejeicao = NULLIF(btrim(p_motivo), '')
    WHERE id = p_inscricao_id;

    RETURN true;
END;
$function$;
