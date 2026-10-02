ALTER TABLE tarefa ADD COLUMN responsavel_usuario_id uuid;
ALTER TABLE tarefa ADD CONSTRAINT fk_tarefa_responsavel FOREIGN KEY (responsavel_usuario_id,tenant_id)
 REFERENCES usuario_tenant(usuario_id,tenant_id) ON DELETE SET NULL (responsavel_usuario_id);
CREATE INDEX ix_tarefa_responsavel ON tarefa(tenant_id,responsavel_usuario_id,status,criado_em,id);
CREATE TABLE indisponibilidade_mes (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), ano integer NOT NULL CHECK(ano BETWEEN 2000 AND 2100),
 mes integer NOT NULL CHECK(mes BETWEEN 1 AND 12), versao bigint NOT NULL DEFAULT 0 CHECK(versao>=0), UNIQUE(tenant_id,ano,mes));
ALTER TABLE indisponibilidade_mes ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON indisponibilidade_mes FROM anon,authenticated;
CREATE INDEX ix_relatorio_evento_data ON escala_eventos(tenant_id,data,id) WHERE referencia=false;
