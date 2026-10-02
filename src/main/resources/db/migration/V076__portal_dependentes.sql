ALTER TABLE pessoa_relacao
 ADD COLUMN portal_consulta boolean NOT NULL DEFAULT false,
 ADD COLUMN portal_resposta boolean NOT NULL DEFAULT false,
 ADD COLUMN portal_versao bigint NOT NULL DEFAULT 0,
 ADD CONSTRAINT pessoa_relacao_portal_resposta CHECK (NOT portal_resposta OR portal_consulta);
CREATE INDEX ix_pessoa_relacao_portal ON pessoa_relacao(tenant_id,responsavel_id,voluntario_id) WHERE portal_consulta;
