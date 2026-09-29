-- Índices das colunas que as consultas já filtram. O tenant_id sozinho
-- (V021) não cobre o filtro por escala, evento, voluntário ou status.
-- IF NOT EXISTS: em produção o SQL manual também passa pelo psql.

CREATE INDEX IF NOT EXISTS idx_escala_eventos_tenant_escala
    ON public.escala_eventos (tenant_id, escala_id);

CREATE INDEX IF NOT EXISTS idx_escala_vagas_tenant_evento
    ON public.escala_vagas (tenant_id, evento_id);

CREATE INDEX IF NOT EXISTS idx_escala_vagas_tenant_voluntario
    ON public.escala_vagas (tenant_id, voluntario_id);

CREATE INDEX IF NOT EXISTS idx_inscricoes_tenant_status
    ON public.inscricoes (tenant_id, status);
