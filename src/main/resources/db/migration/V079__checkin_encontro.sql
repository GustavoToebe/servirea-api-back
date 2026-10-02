CREATE TABLE checkin_sessao(
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 evento_id uuid NOT NULL,
 token_hash varchar(64) NOT NULL UNIQUE,
 criado_por uuid,
 criado_em timestamptz NOT NULL,
 expira_em timestamptz NOT NULL,
 revogado_em timestamptz,
 UNIQUE(tenant_id,id),
 FOREIGN KEY(tenant_id,evento_id) REFERENCES escala_eventos(tenant_id,id) ON DELETE CASCADE);
CREATE INDEX checkin_sessao_evento_idx ON checkin_sessao(tenant_id,evento_id,criado_em DESC);
CREATE TABLE checkin_registro(
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 sessao_id uuid NOT NULL,
 vaga_id uuid NOT NULL REFERENCES escala_vagas(id) ON DELETE CASCADE,
 usuario_id uuid,
 registrado_em timestamptz NOT NULL,
 UNIQUE(tenant_id,vaga_id),
 FOREIGN KEY(tenant_id,sessao_id) REFERENCES checkin_sessao(tenant_id,id) ON DELETE CASCADE);
ALTER TABLE checkin_sessao ENABLE ROW LEVEL SECURITY;
ALTER TABLE checkin_registro ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON checkin_sessao,checkin_registro FROM anon,authenticated;
