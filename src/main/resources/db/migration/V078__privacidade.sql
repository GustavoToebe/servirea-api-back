CREATE TABLE consentimento_historico(
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 pessoa_id uuid NOT NULL,
 tipo varchar(24) NOT NULL CHECK(tipo IN ('WHATSAPP','ANIVERSARIO_EMAIL','ANIVERSARIO_WHATSAPP')),
 concedido boolean NOT NULL,
 fonte varchar(200) NOT NULL,
 registrado_por uuid,
 registrado_em timestamptz NOT NULL,
 FOREIGN KEY(tenant_id,pessoa_id) REFERENCES pessoa(tenant_id,id) ON DELETE CASCADE);
CREATE INDEX consentimento_historico_pessoa_idx ON consentimento_historico(tenant_id,pessoa_id,registrado_em DESC,id);
CREATE TABLE retencao_politica(
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL UNIQUE REFERENCES tenant(id),
 comunicados_dias integer CHECK(comunicados_dias IS NULL OR comunicados_dias BETWEEN 30 AND 3650),
 versao bigint NOT NULL DEFAULT 0);
CREATE TABLE retencao_execucao(
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 executado_em timestamptz NOT NULL,
 executado_por uuid,
 corte timestamptz NOT NULL,
 comunicados_anonimizados integer NOT NULL CHECK(comunicados_anonimizados>=0));
CREATE INDEX retencao_execucao_idx ON retencao_execucao(tenant_id,executado_em DESC);
ALTER TABLE consentimento_historico ENABLE ROW LEVEL SECURITY;
ALTER TABLE retencao_politica ENABLE ROW LEVEL SECURITY;
ALTER TABLE retencao_execucao ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON consentimento_historico,retencao_politica,retencao_execucao FROM anon,authenticated;
