-- V014__rls_policies.sql
-- Fonte: extraído ao vivo do painel do Supabase em 21/09/2026 via
-- pg_policies (documento técnico, seção 20.2). Reproduzido literalmente,
-- nomes de policy e predicados incluídos.
--
-- ATENÇÃO — dívida técnica conhecida e já registrada (seção 16 item 1 do
-- documento técnico; seção 82 do plano mestre): `voluntarios`,
-- `responsaveis`, `escala_eventos` e `escala_vagas` usam
-- `FOR ALL TO authenticated USING (true) WITH CHECK (true)` — ou seja,
-- QUALQUER usuário autenticado lê/escreve tudo, sem papéis. Isso é
-- reproduzido aqui de propósito (fidelidade ao estado real de produção);
-- a correção definitiva é a migração para Spring Security + roles/permissions
-- no backend Java (seção 31 do plano mestre), não um ajuste de RLS agora.

ALTER TABLE public.voluntarios ENABLE ROW LEVEL SECURITY;
CREATE POLICY auth_all_voluntarios ON public.voluntarios
    FOR ALL TO authenticated
    USING (true)
    WITH CHECK (true);

ALTER TABLE public.responsaveis ENABLE ROW LEVEL SECURITY;
CREATE POLICY auth_all_responsaveis ON public.responsaveis
    FOR ALL TO authenticated
    USING (true)
    WITH CHECK (true);

ALTER TABLE public.escala_eventos ENABLE ROW LEVEL SECURITY;
CREATE POLICY auth_all_escala_eventos ON public.escala_eventos
    FOR ALL TO authenticated
    USING (true)
    WITH CHECK (true);

ALTER TABLE public.escala_vagas ENABLE ROW LEVEL SECURITY;
CREATE POLICY auth_all_escala_vagas ON public.escala_vagas
    FOR ALL TO authenticated
    USING (true)
    WITH CHECK (true);

ALTER TABLE public.escalas ENABLE ROW LEVEL SECURITY;

CREATE POLICY auth_select_escalas ON public.escalas
    FOR SELECT TO authenticated
    USING (true);

CREATE POLICY auth_insert_escalas ON public.escalas
    FOR INSERT TO authenticated
    WITH CHECK (true);

CREATE POLICY auth_update_escalas ON public.escalas
    FOR UPDATE TO authenticated
    USING (true)
    WITH CHECK (true);

CREATE POLICY auth_delete_only_cancelled_escalas ON public.escalas
    FOR DELETE TO authenticated
    USING (status = 'CANCELADA'::public.status_escala);

-- inscricoes / inscricao_responsaveis: mais restritas do que as tabelas
-- acima (seção 20.2 do documento técnico) — não existe policy de INSERT
-- nem DELETE para authenticated em `inscricoes`; só é possível criar via
-- RPC com service_role (V011/V013), e só é possível atualizar registros
-- com status = PENDENTE.

ALTER TABLE public.inscricoes ENABLE ROW LEVEL SECURITY;

CREATE POLICY inscricoes_authenticated_select ON public.inscricoes
    FOR SELECT TO authenticated
    USING (true);

CREATE POLICY inscricoes_authenticated_update_pending ON public.inscricoes
    FOR UPDATE TO authenticated
    USING (status = 'PENDENTE'::public.status_inscricao)
    WITH CHECK (status = 'PENDENTE'::public.status_inscricao);

ALTER TABLE public.inscricao_responsaveis ENABLE ROW LEVEL SECURITY;

CREATE POLICY inscricao_responsaveis_authenticated_select ON public.inscricao_responsaveis
    FOR SELECT TO authenticated
    USING (true);

CREATE POLICY inscricao_responsaveis_authenticated_insert ON public.inscricao_responsaveis
    FOR INSERT TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.inscricoes i
            WHERE i.id = inscricao_responsaveis.inscricao_id
              AND i.status = 'PENDENTE'::public.status_inscricao
        )
    );

CREATE POLICY inscricao_responsaveis_authenticated_update ON public.inscricao_responsaveis
    FOR UPDATE TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.inscricoes i
            WHERE i.id = inscricao_responsaveis.inscricao_id
              AND i.status = 'PENDENTE'::public.status_inscricao
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.inscricoes i
            WHERE i.id = inscricao_responsaveis.inscricao_id
              AND i.status = 'PENDENTE'::public.status_inscricao
        )
    );

CREATE POLICY inscricao_responsaveis_authenticated_delete ON public.inscricao_responsaveis
    FOR DELETE TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.inscricoes i
            WHERE i.id = inscricao_responsaveis.inscricao_id
              AND i.status = 'PENDENTE'::public.status_inscricao
        )
    );
