CREATE TABLE arquivo_pendencia(
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 caminho varchar(500) NOT NULL,
 tipo varchar(10) NOT NULL CHECK(tipo IN ('UPLOAD','REMOCAO')),
 criado_em timestamptz NOT NULL,
 tentativas integer NOT NULL DEFAULT 0 CHECK(tentativas>=0),
 proxima_tentativa timestamptz NOT NULL,
 erro varchar(200),
 reservado_por uuid,
 reserva_ate timestamptz,
 UNIQUE(tenant_id,caminho,tipo),
 CHECK((reservado_por IS NULL)=(reserva_ate IS NULL)));
CREATE INDEX arquivo_pendencia_fila_idx ON arquivo_pendencia(tenant_id,proxima_tentativa);
ALTER TABLE arquivo_pendencia ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON arquivo_pendencia FROM anon,authenticated;
