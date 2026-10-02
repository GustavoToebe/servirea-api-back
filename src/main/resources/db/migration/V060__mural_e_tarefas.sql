-- Mural e tarefas internas: sem publicação anônima, exclusão física ou anexos.
CREATE TABLE mural_aviso (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), versao bigint NOT NULL DEFAULT 0,
 titulo varchar(160) NOT NULL, descricao varchar(4000) NOT NULL,
 status varchar(24) NOT NULL CHECK(status IN ('PUBLICADO','ARQUIVADO')), prazo date,
 criado_em timestamptz NOT NULL, atualizado_em timestamptz NOT NULL);
CREATE INDEX ix_mural_aviso_lista ON mural_aviso(tenant_id,status,criado_em DESC,id DESC);
CREATE INDEX ix_mural_aviso_recentes ON mural_aviso(tenant_id,criado_em DESC,id DESC);
ALTER TABLE mural_aviso ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON mural_aviso FROM anon,authenticated;
CREATE TABLE tarefa (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), versao bigint NOT NULL DEFAULT 0,
 titulo varchar(160) NOT NULL, descricao varchar(4000) NOT NULL,
 status varchar(24) NOT NULL CHECK(status IN ('ABERTA','EM_ANDAMENTO','CONCLUIDA','CANCELADA')), prazo date, equipe varchar(120),
 criado_em timestamptz NOT NULL, atualizado_em timestamptz NOT NULL);
CREATE INDEX ix_tarefa_lista ON tarefa(tenant_id,status,criado_em DESC,id DESC);
CREATE INDEX ix_tarefa_recentes ON tarefa(tenant_id,criado_em DESC,id DESC);
ALTER TABLE tarefa ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON tarefa FROM anon,authenticated;
