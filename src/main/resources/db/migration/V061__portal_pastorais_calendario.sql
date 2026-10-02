-- Associação explícita; nunca inferir identidade por nome, CPF ou e-mail compartilhado.
ALTER TABLE usuario_tenant ADD COLUMN pessoa_id uuid;
ALTER TABLE usuario_tenant ADD CONSTRAINT fk_usuario_pessoa_tenant FOREIGN KEY(tenant_id,pessoa_id) REFERENCES pessoa(tenant_id,id);
CREATE UNIQUE INDEX uq_usuario_pessoa_tenant ON usuario_tenant(tenant_id,pessoa_id) WHERE pessoa_id IS NOT NULL;
CREATE TABLE pastoral_equipe (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant(id),nome varchar(120) NOT NULL,
 descricao varchar(1000),ativo boolean NOT NULL,versao bigint NOT NULL DEFAULT 0,
 UNIQUE(tenant_id,id));
CREATE INDEX ix_pastoral_lista ON pastoral_equipe(tenant_id,nome,id);
CREATE TABLE pastoral_membro (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant(id),equipe_id uuid NOT NULL,pessoa_id uuid NOT NULL,
 papel varchar(20) NOT NULL CHECK(papel IN ('MEMBRO','COORDENADOR')),ativo boolean NOT NULL,versao bigint NOT NULL DEFAULT 0,
 FOREIGN KEY(tenant_id,equipe_id) REFERENCES pastoral_equipe(tenant_id,id),
 FOREIGN KEY(tenant_id,pessoa_id) REFERENCES pessoa(tenant_id,id),UNIQUE(tenant_id,equipe_id,pessoa_id));
CREATE INDEX ix_pastoral_membros ON pastoral_membro(tenant_id,equipe_id,id);
-- Global como usuario_tenant: permite resolver o contexto ANTES da sessão tenant-aware.
CREATE TABLE calendario_assinatura (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant(id),usuario_id uuid NOT NULL REFERENCES usuario(id),
 pessoa_id uuid NOT NULL,token_hash varchar(64) NOT NULL UNIQUE,expira_em timestamptz NOT NULL,criado_em timestamptz NOT NULL,
 FOREIGN KEY(tenant_id,pessoa_id) REFERENCES pessoa(tenant_id,id));
CREATE UNIQUE INDEX uq_calendario_usuario ON calendario_assinatura(tenant_id,usuario_id);
ALTER TABLE pastoral_equipe ENABLE ROW LEVEL SECURITY;
ALTER TABLE pastoral_membro ENABLE ROW LEVEL SECURITY;
ALTER TABLE calendario_assinatura ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON pastoral_equipe,pastoral_membro,calendario_assinatura FROM anon,authenticated;
