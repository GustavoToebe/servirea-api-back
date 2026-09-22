-- V025__table_disponibilidade_voluntario.sql
-- Disponibilidade do voluntário (item 12 da seção 122, detalhado na seção
-- 131.5): "nova entidade tenant-aware, ex. disponibilidade_voluntario
-- (voluntario_id, dia_semana ou data, período), a ser usada pelo picker de
-- candidatos (seção 49) como filtro adicional".
--
-- Nota (Fase 11): esta migration cria só a tabela em si. A integração com o
-- picker de candidatos (seção 49) fica de fora desta rodada — o endpoint do
-- picker em si ainda não existe no backend Java (ver README.md/plano
-- mestre, "Próximos passos"); integrá-lo é um passo futuro separado.

CREATE TYPE public.periodo_dia AS ENUM (
    'MANHA',
    'TARDE',
    'NOITE'
);

CREATE TABLE public.disponibilidade_voluntario (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid NOT NULL REFERENCES public.tenant(id),
    voluntario_id  uuid NOT NULL REFERENCES public.voluntarios(id) ON DELETE CASCADE,
    -- Exatamente um dos dois é preenchido: dia_semana (disponibilidade
    -- recorrente, ex. "toda quarta") OU data (exceção/disponibilidade
    -- pontual, ex. "só no dia 25/12"). Nomes de dia_semana são os do
    -- java.time.DayOfWeek em inglês (MONDAY..SUNDAY) — não um ENUM nativo
    -- do Postgres próprio do domínio, já que é um tipo do JDK, não uma
    -- lista de valores que este projeto define (mesmo raciocínio já usado
    -- em voluntarios.horario_estudo, ver V003/Voluntario.HorarioEstudo).
    dia_semana     text CHECK (dia_semana IN
                       ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    data           date,
    periodo        public.periodo_dia NOT NULL,
    observacao     text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT disponibilidade_voluntario_dia_xor_data
        CHECK ((dia_semana IS NOT NULL) <> (data IS NOT NULL))
);

-- Evita duplicar a mesma disponibilidade recorrente (mesmo voluntário, mesmo
-- dia da semana, mesmo período) ou a mesma exceção pontual (mesmo
-- voluntário, mesma data, mesmo período).
CREATE UNIQUE INDEX ux_disponibilidade_recorrente
    ON public.disponibilidade_voluntario (voluntario_id, dia_semana, periodo)
    WHERE dia_semana IS NOT NULL;

CREATE UNIQUE INDEX ux_disponibilidade_pontual
    ON public.disponibilidade_voluntario (voluntario_id, data, periodo)
    WHERE data IS NOT NULL;

CREATE INDEX idx_disponibilidade_voluntario_tenant ON public.disponibilidade_voluntario (tenant_id);

COMMENT ON TABLE public.disponibilidade_voluntario IS
    'Dias/horários em que um voluntário pode servir — filtro adicional planejado para o picker de candidatos (seção 49), ainda não integrado (ver README.md).';
