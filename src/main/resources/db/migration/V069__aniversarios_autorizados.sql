CREATE TABLE aniversario_config(id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant(id),canal varchar(20) NOT NULL CHECK(canal IN ('EMAIL','WHATSAPP')),ativo boolean NOT NULL DEFAULT false,layout_id uuid,versao bigint NOT NULL DEFAULT 0,UNIQUE(tenant_id,canal),FOREIGN KEY(tenant_id,layout_id) REFERENCES layout_envio(tenant_id,id) ON DELETE SET NULL(layout_id));
CREATE TABLE aniversario_autorizacao(id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant(id),pessoa_id uuid NOT NULL,canal varchar(20) NOT NULL CHECK(canal IN ('EMAIL','WHATSAPP')),autorizado boolean NOT NULL DEFAULT false,fonte varchar(200) NOT NULL,registrado_em timestamptz NOT NULL,versao bigint NOT NULL DEFAULT 0,UNIQUE(tenant_id,pessoa_id,canal),FOREIGN KEY(tenant_id,pessoa_id) REFERENCES pessoa(tenant_id,id) ON DELETE CASCADE);
CREATE TABLE aniversario_execucao(id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant(id),pessoa_id uuid NOT NULL,canal varchar(20) NOT NULL,ano integer NOT NULL,dia date NOT NULL,comunicado_id uuid,status varchar(24) NOT NULL,UNIQUE(tenant_id,pessoa_id,canal,ano),FOREIGN KEY(tenant_id,comunicado_id) REFERENCES comunicado(tenant_id,id) ON DELETE SET NULL(comunicado_id));
CREATE INDEX aniversario_execucao_comunicado_idx ON aniversario_execucao(tenant_id,comunicado_id);
ALTER TABLE aniversario_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE aniversario_autorizacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE aniversario_execucao ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON aniversario_config,aniversario_autorizacao,aniversario_execucao FROM anon,authenticated;
