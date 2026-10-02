-- text_pattern_ops de V073 atende prefixo, mas não substitui a ordenação no locale.
-- Permite primeira página sem filtro sem ordenar toda a paróquia.
CREATE INDEX ix_pessoa_seletor_ordenacao ON pessoa(tenant_id,lower(nome_completo),id);
