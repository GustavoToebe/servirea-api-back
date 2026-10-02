CREATE TABLE consumo_historico(id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant(id),dia date NOT NULL,consultado_em timestamptz NOT NULL,dados text NOT NULL CHECK(length(dados)<=32768),UNIQUE(tenant_id,dia));
CREATE INDEX consumo_historico_dia_idx ON consumo_historico(dia);
ALTER TABLE consumo_historico ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON consumo_historico FROM anon,authenticated;
