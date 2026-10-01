ALTER TABLE comunicado_destinatario ADD COLUMN proxima_tentativa timestamptz NOT NULL DEFAULT '1970-01-01T00:00:00Z',
 ADD COLUMN reservado_por uuid, ADD COLUMN reserva_ate timestamptz,
 ADD CONSTRAINT ck_comunicado_destinatario_reserva CHECK ((reservado_por IS NULL)=(reserva_ate IS NULL));
CREATE INDEX ix_destinatario_pronto ON comunicado_destinatario(tenant_id,proxima_tentativa,id) WHERE status='PENDENTE';
CREATE TABLE fila_envio_janela (id varchar(100) PRIMARY KEY,proximo_permitido timestamptz NOT NULL DEFAULT '1970-01-01T00:00:00Z', reservado_por uuid,reserva_ate timestamptz,
 CONSTRAINT ck_janela_reserva CHECK ((reservado_por IS NULL)=(reserva_ate IS NULL)));
INSERT INTO fila_envio_janela(id) VALUES('EMAIL');
INSERT INTO fila_envio_janela(id) SELECT 'WHATSAPP:'||id::text FROM tenant;
ALTER TABLE fila_envio_janela ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON fila_envio_janela FROM anon,authenticated;
