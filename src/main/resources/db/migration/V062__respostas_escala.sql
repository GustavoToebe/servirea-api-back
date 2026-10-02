-- Resposta de intenção é independente de presença e nunca desaloca automaticamente.
ALTER TABLE escala_vagas ADD COLUMN resposta varchar(20) NOT NULL DEFAULT 'PENDENTE'
 CHECK(resposta IN ('PENDENTE','CONFIRMADA','RECUSADA'));
ALTER TABLE escala_vagas ADD COLUMN resposta_em timestamptz;
ALTER TABLE escala_vagas ADD COLUMN resposta_versao bigint NOT NULL DEFAULT 0;
CREATE TABLE escala_resposta_historico (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id),
 escala_id uuid NOT NULL, vaga_id uuid NOT NULL, pessoa_id uuid NOT NULL, usuario_id uuid NOT NULL,
 resposta varchar(20) NOT NULL CHECK(resposta IN ('CONFIRMADA','RECUSADA')),
 respondido_em timestamptz NOT NULL, versao bigint NOT NULL);
-- IDs são referências históricas como audit_log; não apagar com orphanRemoval/recriação da escala.
CREATE INDEX ix_resposta_historico ON escala_resposta_historico(tenant_id,vaga_id,pessoa_id,respondido_em,id);
ALTER TABLE escala_resposta_historico ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON escala_resposta_historico FROM anon,authenticated;
