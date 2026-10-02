-- Referências de vaga/pessoa preservadas como histórico, sem cascata de exclusão.
CREATE TABLE escala_candidatura (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant(id),escala_id uuid NOT NULL,
 vaga_id uuid NOT NULL,pessoa_id uuid NOT NULL,usuario_id uuid NOT NULL,vaga_versao bigint NOT NULL,
 situacao varchar(20) NOT NULL CHECK(situacao IN ('PENDENTE','APROVADA','RECUSADA','DESISTIDA','EXPIRADA')),
 versao bigint NOT NULL DEFAULT 0,criada_em timestamptz NOT NULL,atualizada_em timestamptz NOT NULL,
 celebracao varchar(255) NOT NULL,inicio timestamp NOT NULL,funcao varchar(20) NOT NULL,
 UNIQUE(tenant_id,vaga_id,pessoa_id,vaga_versao));
CREATE INDEX ix_candidatura_pessoal ON escala_candidatura(tenant_id,pessoa_id,criada_em,id);
CREATE INDEX ix_candidatura_escala ON escala_candidatura(tenant_id,escala_id,criada_em,id);
CREATE INDEX ix_candidatura_vaga ON escala_candidatura(tenant_id,vaga_id,situacao);
ALTER TABLE escala_candidatura ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON escala_candidatura FROM anon,authenticated;
