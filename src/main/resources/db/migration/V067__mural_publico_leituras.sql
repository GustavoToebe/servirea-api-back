ALTER TABLE mural_aviso ADD COLUMN publico varchar(24) NOT NULL DEFAULT 'TODOS' CHECK (publico IN ('TODOS','SELECIONADOS'));
ALTER TABLE mural_aviso ADD CONSTRAINT mural_tenant_id_unique UNIQUE(tenant_id,id);
CREATE TABLE mural_destinatario(id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant(id),aviso_id uuid NOT NULL,usuario_id uuid NOT NULL,
 UNIQUE(tenant_id,aviso_id,usuario_id),FOREIGN KEY(tenant_id,aviso_id) REFERENCES mural_aviso(tenant_id,id) ON DELETE CASCADE,
 FOREIGN KEY(usuario_id,tenant_id) REFERENCES usuario_tenant(usuario_id,tenant_id) ON DELETE CASCADE);
CREATE TABLE mural_leitura(id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant(id),aviso_id uuid NOT NULL,usuario_id uuid NOT NULL,versao_aviso bigint NOT NULL,lido_em timestamptz NOT NULL,
 UNIQUE(tenant_id,aviso_id,usuario_id),FOREIGN KEY(tenant_id,aviso_id) REFERENCES mural_aviso(tenant_id,id) ON DELETE CASCADE,
 FOREIGN KEY(usuario_id,tenant_id) REFERENCES usuario_tenant(usuario_id,tenant_id) ON DELETE CASCADE);
ALTER TABLE mural_destinatario ENABLE ROW LEVEL SECURITY;
ALTER TABLE mural_leitura ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON mural_destinatario,mural_leitura FROM anon,authenticated;
