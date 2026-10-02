CREATE TABLE site_paroquia(id uuid PRIMARY KEY,tenant_id uuid NOT NULL UNIQUE REFERENCES tenant(id),versao bigint NOT NULL DEFAULT 0,rascunho text NOT NULL CHECK(length(rascunho)<=65536),publicado text CHECK(length(publicado)<=65536),publicado_em timestamptz);
ALTER TABLE site_paroquia ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON site_paroquia FROM anon,authenticated;
