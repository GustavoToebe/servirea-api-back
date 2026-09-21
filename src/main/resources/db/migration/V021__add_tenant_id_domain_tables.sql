-- V021__add_tenant_id_domain_tables.sql
-- Fase 3/4 (seção 17/23/24/26/103/104 do plano mestre): adiciona tenant_id
-- às 7 tabelas de domínio (Discriminator-based Multi-Tenancy, seção 17) e
-- troca as FKs simples entre elas por FKs compostas tenant-aware (seção
-- 23), para que o próprio banco impeça referências cross-tenant (o filtro
-- do Hibernate/@TenantId sozinho não garante integridade referencial,
-- só a leitura/escrita via JPA).
--
-- Backfill: todo dado já existente (V003-V009) é atribuído ao tenant
-- inicial criado na V020.

-- 1) Coluna tenant_id (nullable por enquanto, para permitir backfill antes
--    do NOT NULL).
ALTER TABLE public.voluntarios            ADD COLUMN tenant_id uuid REFERENCES public.tenant(id);
ALTER TABLE public.responsaveis           ADD COLUMN tenant_id uuid REFERENCES public.tenant(id);
ALTER TABLE public.escalas                ADD COLUMN tenant_id uuid REFERENCES public.tenant(id);
ALTER TABLE public.escala_eventos         ADD COLUMN tenant_id uuid REFERENCES public.tenant(id);
ALTER TABLE public.escala_vagas           ADD COLUMN tenant_id uuid REFERENCES public.tenant(id);
ALTER TABLE public.inscricoes             ADD COLUMN tenant_id uuid REFERENCES public.tenant(id);
ALTER TABLE public.inscricao_responsaveis ADD COLUMN tenant_id uuid REFERENCES public.tenant(id);

-- 2) Backfill: tudo que já existe vai para o tenant inicial (V020, seção 114).
UPDATE public.voluntarios            SET tenant_id = '00000000-0000-0000-0000-000000000001';
UPDATE public.responsaveis           SET tenant_id = '00000000-0000-0000-0000-000000000001';
UPDATE public.escalas                SET tenant_id = '00000000-0000-0000-0000-000000000001';
UPDATE public.escala_eventos         SET tenant_id = '00000000-0000-0000-0000-000000000001';
UPDATE public.escala_vagas           SET tenant_id = '00000000-0000-0000-0000-000000000001';
UPDATE public.inscricoes             SET tenant_id = '00000000-0000-0000-0000-000000000001';
UPDATE public.inscricao_responsaveis SET tenant_id = '00000000-0000-0000-0000-000000000001';

-- 3) NOT NULL (seção 26: "Todas deverão possuir tenant_id NOT NULL").
ALTER TABLE public.voluntarios            ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE public.responsaveis           ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE public.escalas                ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE public.escala_eventos         ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE public.escala_vagas           ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE public.inscricoes             ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE public.inscricao_responsaveis ALTER COLUMN tenant_id SET NOT NULL;

-- 4) UNIQUE (tenant_id, id) nas tabelas "pai" referenciadas por outra
--    tabela de domínio via id, necessário para trocar as FKs simples por
--    FKs compostas tenant-aware no próximo passo (seção 23, exemplo
--    literal da própria seção: "UNIQUE (tenant_id, id) em voluntario").
ALTER TABLE public.voluntarios    ADD CONSTRAINT voluntarios_tenant_id_id_key    UNIQUE (tenant_id, id);
ALTER TABLE public.escalas        ADD CONSTRAINT escalas_tenant_id_id_key        UNIQUE (tenant_id, id);
ALTER TABLE public.escala_eventos ADD CONSTRAINT escala_eventos_tenant_id_id_key UNIQUE (tenant_id, id);
ALTER TABLE public.inscricoes     ADD CONSTRAINT inscricoes_tenant_id_id_key     UNIQUE (tenant_id, id);

-- 5) Trocar as FKs simples por FKs compostas tenant-aware (seção 23):
--    impede, por exemplo, um "responsavel" do tenant A apontar para um
--    "voluntario" do tenant B, mesmo que a aplicação nunca mostre isso.
--    Nomes de constraint auto-gerados pelo Postgres nas migrations
--    originais (V004/V006/V007/V008/V009), confirmados por não terem
--    CONSTRAINT explícito lá: <tabela>_<coluna>_fkey.

-- responsaveis.voluntario_id -> voluntarios (mesmo tenant)
ALTER TABLE public.responsaveis DROP CONSTRAINT responsaveis_voluntario_id_fkey;
ALTER TABLE public.responsaveis
    ADD CONSTRAINT responsaveis_tenant_voluntario_fkey
    FOREIGN KEY (tenant_id, voluntario_id) REFERENCES public.voluntarios (tenant_id, id)
    ON DELETE CASCADE;

-- escala_eventos.escala_id -> escalas (mesmo tenant)
ALTER TABLE public.escala_eventos DROP CONSTRAINT escala_eventos_escala_id_fkey;
ALTER TABLE public.escala_eventos
    ADD CONSTRAINT escala_eventos_tenant_escala_fkey
    FOREIGN KEY (tenant_id, escala_id) REFERENCES public.escalas (tenant_id, id)
    ON DELETE CASCADE;

-- escala_vagas.evento_id -> escala_eventos (mesmo tenant)
ALTER TABLE public.escala_vagas DROP CONSTRAINT escala_vagas_evento_id_fkey;
ALTER TABLE public.escala_vagas
    ADD CONSTRAINT escala_vagas_tenant_evento_fkey
    FOREIGN KEY (tenant_id, evento_id) REFERENCES public.escala_eventos (tenant_id, id)
    ON DELETE CASCADE;

-- escala_vagas.voluntario_id -> voluntarios (mesmo tenant; coluna nullable
-- - "ON DELETE SET NULL (voluntario_id)" seta só essa coluna, nunca o
-- tenant_id, que continua NOT NULL; sintaxe de coluna específica do
-- Postgres 15+, compatível com o Postgres 16 usado neste projeto)
ALTER TABLE public.escala_vagas DROP CONSTRAINT escala_vagas_voluntario_id_fkey;
ALTER TABLE public.escala_vagas
    ADD CONSTRAINT escala_vagas_tenant_voluntario_fkey
    FOREIGN KEY (tenant_id, voluntario_id) REFERENCES public.voluntarios (tenant_id, id)
    ON DELETE SET NULL (voluntario_id);

-- inscricoes.voluntario_id -> voluntarios (mesmo tenant; nullable, unique
-- já garantida por inscricoes_voluntario_id_key, que não muda - ver nota
-- de projeto abaixo)
ALTER TABLE public.inscricoes DROP CONSTRAINT inscricoes_voluntario_id_fkey;
ALTER TABLE public.inscricoes
    ADD CONSTRAINT inscricoes_tenant_voluntario_fkey
    FOREIGN KEY (tenant_id, voluntario_id) REFERENCES public.voluntarios (tenant_id, id)
    ON DELETE SET NULL (voluntario_id);

-- inscricao_responsaveis.inscricao_id -> inscricoes (mesmo tenant)
ALTER TABLE public.inscricao_responsaveis DROP CONSTRAINT inscricao_responsaveis_inscricao_id_fkey;
ALTER TABLE public.inscricao_responsaveis
    ADD CONSTRAINT inscricao_responsaveis_tenant_inscricao_fkey
    FOREIGN KEY (tenant_id, inscricao_id) REFERENCES public.inscricoes (tenant_id, id)
    ON DELETE CASCADE;

-- Nota de projeto: inscricoes_voluntario_id_key UNIQUE (voluntario_id) não
-- foi ampliada para (tenant_id, voluntario_id) porque voluntario_id já é
-- por si só uma referência tenant-scoped (via voluntarios.id, que só
-- existe dentro de um tenant) - manter a constraint mais estrita (um
-- voluntario só pode estar ligado a uma inscricao, ponto) é correto e não
-- precisa do tenant_id junto.

-- 6) Índices em tenant_id - toda query tenant-aware vai filtrar por essa
--    coluna (Hibernate @TenantId gera WHERE tenant_id = ? automaticamente
--    em toda consulta JPA sobre entidades tenant-aware).
CREATE INDEX idx_voluntarios_tenant_id            ON public.voluntarios (tenant_id);
CREATE INDEX idx_responsaveis_tenant_id           ON public.responsaveis (tenant_id);
CREATE INDEX idx_escalas_tenant_id                ON public.escalas (tenant_id);
CREATE INDEX idx_escala_eventos_tenant_id         ON public.escala_eventos (tenant_id);
CREATE INDEX idx_escala_vagas_tenant_id           ON public.escala_vagas (tenant_id);
CREATE INDEX idx_inscricoes_tenant_id             ON public.inscricoes (tenant_id);
CREATE INDEX idx_inscricao_responsaveis_tenant_id ON public.inscricao_responsaveis (tenant_id);
