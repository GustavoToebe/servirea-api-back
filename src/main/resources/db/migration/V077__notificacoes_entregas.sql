CREATE TABLE notificacao_config(
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 origem varchar(20) NOT NULL CHECK(origem IN ('ESCALA','MURAL')),
 canal varchar(20) NOT NULL CHECK(canal IN ('EMAIL','WHATSAPP')),
 ativo boolean NOT NULL DEFAULT false,
 versao bigint NOT NULL DEFAULT 0,
 UNIQUE(tenant_id,origem,canal));
CREATE TABLE notificacao_entrega(
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 origem varchar(20) NOT NULL CHECK(origem IN ('ESCALA','MURAL')),
 referencia_id uuid NOT NULL,
 referencia_versao bigint NOT NULL,
 canal varchar(20) NOT NULL CHECK(canal IN ('EMAIL','WHATSAPP')),
 gatilho varchar(12) NOT NULL CHECK(gatilho IN ('MANUAL','AUTOMATICO')),
 total integer NOT NULL CHECK(total>=0),
 ignorados integer NOT NULL CHECK(ignorados>=0),
 comunicado_id uuid,
 criado_por uuid,
 criado_em timestamptz NOT NULL,
 UNIQUE(tenant_id,origem,referencia_id,referencia_versao,canal),
 FOREIGN KEY(tenant_id,comunicado_id) REFERENCES comunicado(tenant_id,id) ON DELETE SET NULL(comunicado_id));
CREATE INDEX notificacao_entrega_lista_idx ON notificacao_entrega(tenant_id,criado_em DESC,id);
CREATE INDEX notificacao_entrega_comunicado_idx ON notificacao_entrega(tenant_id,comunicado_id);
ALTER TABLE notificacao_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE notificacao_entrega ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON notificacao_config,notificacao_entrega FROM anon,authenticated;
