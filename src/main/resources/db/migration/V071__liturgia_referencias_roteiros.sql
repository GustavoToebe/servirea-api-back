CREATE TABLE liturgia_referencia(id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant(id),versao bigint NOT NULL DEFAULT 0,titulo varchar(160) NOT NULL,fonte varchar(200) NOT NULL,url varchar(1000),observacao varchar(2000),ativo boolean NOT NULL DEFAULT true,UNIQUE(tenant_id,id));
CREATE INDEX liturgia_referencia_lista ON liturgia_referencia(tenant_id,lower(titulo),id);
CREATE TABLE liturgia_roteiro(id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant(id),versao bigint NOT NULL DEFAULT 0,titulo varchar(160) NOT NULL,celebracao varchar(200) NOT NULL,passos text NOT NULL CHECK(length(passos)<=120000),ativo boolean NOT NULL DEFAULT true);
CREATE INDEX liturgia_roteiro_lista ON liturgia_roteiro(tenant_id,lower(titulo),id);
ALTER TABLE liturgia_referencia ENABLE ROW LEVEL SECURITY;
ALTER TABLE liturgia_roteiro ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON liturgia_referencia,liturgia_roteiro FROM anon,authenticated;
