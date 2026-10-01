-- Registro de conclusão e idempotência; não guarda o CSV nem dados de pessoas.
CREATE TABLE importacao_pessoa (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), chave uuid NOT NULL,
 hash_arquivo varchar(64) NOT NULL, quantidade integer NOT NULL CHECK(quantidade BETWEEN 1 AND 100),
 competencia date NOT NULL CHECK(extract(day from competencia)=1), criado_em timestamptz NOT NULL,
 UNIQUE(tenant_id,chave));
CREATE INDEX ix_importacao_pessoa_mes ON importacao_pessoa(tenant_id,competencia);
ALTER TABLE importacao_pessoa ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON importacao_pessoa FROM anon,authenticated;
