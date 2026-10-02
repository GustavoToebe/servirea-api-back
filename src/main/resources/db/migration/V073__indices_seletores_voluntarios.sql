-- Busca pelo início do nome: índice compatível com LIKE no locale do banco.
CREATE INDEX IF NOT EXISTS ix_pessoa_seletor_prefixo ON pessoa(tenant_id,lower(nome_completo) text_pattern_ops,id);
CREATE INDEX IF NOT EXISTS ix_voluntarios_seletor_ativo ON voluntarios(tenant_id,ativo,id);
