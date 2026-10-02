-- Histórico sem cascata; uma solicitação ativa por vaga, mantida sob lock da escala.
CREATE TABLE escala_troca (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant(id),escala_id uuid NOT NULL,vaga_id uuid NOT NULL,
 solicitante_id uuid NOT NULL,substituto_id uuid NOT NULL,solicitante_usuario_id uuid NOT NULL,substituto_usuario_id uuid NOT NULL,
 vaga_versao bigint NOT NULL,situacao varchar(30) NOT NULL CHECK(situacao IN ('AGUARDANDO_ACEITE','ACEITA','APROVADA','RECUSADA','CANCELADA','EXPIRADA')),
 versao bigint NOT NULL DEFAULT 0,criada_em timestamptz NOT NULL,atualizada_em timestamptz NOT NULL,
 celebracao varchar(255) NOT NULL,inicio timestamp NOT NULL,funcao varchar(20) NOT NULL,
 CHECK(solicitante_id<>substituto_id));
CREATE UNIQUE INDEX ux_troca_ativa_vaga ON escala_troca(tenant_id,vaga_id) WHERE situacao IN ('AGUARDANDO_ACEITE','ACEITA');
CREATE INDEX ix_troca_solicitante ON escala_troca(tenant_id,solicitante_id,criada_em,id);
CREATE INDEX ix_troca_substituto ON escala_troca(tenant_id,substituto_id,criada_em,id);
CREATE INDEX ix_troca_escala ON escala_troca(tenant_id,escala_id,criada_em,id);
ALTER TABLE escala_troca ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON escala_troca FROM anon,authenticated;
