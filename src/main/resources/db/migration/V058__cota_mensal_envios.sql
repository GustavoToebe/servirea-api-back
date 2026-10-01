-- Uma unidade por mensagem lógica, reservada antes da primeira chamada ao provedor.
ALTER TABLE comunicado_destinatario ADD COLUMN cota_competencia date
    CHECK (extract(day from cota_competencia) = 1);
-- Só reconstruir envios com instante confirmado; falhas antigas não têm primeira tentativa conhecida.
UPDATE comunicado_destinatario
SET cota_competencia = date_trunc('month', enviado_em AT TIME ZONE 'America/Sao_Paulo')::date
WHERE enviado_em IS NOT NULL;
CREATE INDEX idx_destinatario_cota_mes ON comunicado_destinatario (tenant_id, cota_competencia, comunicado_id)
    WHERE cota_competencia IS NOT NULL;
