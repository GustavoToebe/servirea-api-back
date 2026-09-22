-- V023__ajustes_fases_7_8_9.sql
-- Dois ajustes de schema identificados por revisão (não por build) durante
-- o planejamento conjunto das Fases 7 (Storage), 8 (Inscrições públicas) e
-- 9 (Escalas), em 22/09/2026 — ver README.md/plano mestre seção 106-109
-- para o relato completo de cada achado.

-- 1) Repontar as FKs que ainda apontavam para auth.users(id) — tabela do
--    Supabase Auth, que a seção 32/83 do plano mestre já previa remover
--    progressivamente. Desde a Fase 5 (seção 105), a identidade de ator da
--    aplicação é a própria tabela public.usuario (V017), com UUIDs
--    próprios que NÃO correspondem a auth.users. Sem este ajuste, gravar
--    um usuario.id nessas colunas falharia a FK em tempo de execução no
--    primeiro "criar escala"/"aprovar inscrição"/"rejeitar inscrição" via
--    JWT (Fases 8/9) — corrigido agora, antes de existir código que
--    dependa disso.
--
--    Nomes de constraint: auto-gerados pelo Postgres nas migrations
--    originais (V005/V008, sem CONSTRAINT explícito lá) — <tabela>_<coluna>_fkey,
--    mesmo padrão já usado/confirmado em V021.

ALTER TABLE public.escalas DROP CONSTRAINT escalas_created_by_fkey;
ALTER TABLE public.escalas
    ADD CONSTRAINT escalas_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES public.usuario (id) ON DELETE SET NULL;

ALTER TABLE public.inscricoes DROP CONSTRAINT inscricoes_aprovado_por_fkey;
ALTER TABLE public.inscricoes
    ADD CONSTRAINT inscricoes_aprovado_por_fkey
    FOREIGN KEY (aprovado_por) REFERENCES public.usuario (id);

ALTER TABLE public.inscricoes DROP CONSTRAINT inscricoes_rejeitado_por_fkey;
ALTER TABLE public.inscricoes
    ADD CONSTRAINT inscricoes_rejeitado_por_fkey
    FOREIGN KEY (rejeitado_por) REFERENCES public.usuario (id);

-- Nota: a coluna created_by de escalas tinha DEFAULT auth.uid() (V005),
-- função do Supabase Auth GoTrue que não existe fora do Supabase e que a
-- aplicação Java nunca vai invocar (o valor sempre é setado explicitamente
-- pelo EscalaService a partir do AuthenticatedUser da requisição) — o
-- DEFAULT fica inofensivo (nunca é exercitado por INSERT vindo do
-- Hibernate, que sempre envia o valor da coluna), então não é removido
-- aqui para não introduzir uma mudança de schema sem necessidade real.

-- 2) Controle otimista de versão em escalas (seção 47 do plano mestre:
--    "@Version + HTTP 409 em conflito de edição concorrente") — coluna
--    ainda não existia; a entidade Escala (Fase 9) mapeia @Version nela.
ALTER TABLE public.escalas ADD COLUMN version bigint NOT NULL DEFAULT 0;
