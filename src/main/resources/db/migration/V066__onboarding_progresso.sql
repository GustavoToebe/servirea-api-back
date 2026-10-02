CREATE TABLE onboarding_progresso (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL UNIQUE REFERENCES tenant(id),versao bigint NOT NULL DEFAULT 0 CHECK(versao>=0),
 paroquia boolean NOT NULL DEFAULT false,convite boolean NOT NULL DEFAULT false,pessoas boolean NOT NULL DEFAULT false,
 voluntarios boolean NOT NULL DEFAULT false,escala boolean NOT NULL DEFAULT false,convite_dispensado boolean NOT NULL DEFAULT false,
 iniciado_em timestamptz NOT NULL,atualizado_em timestamptz NOT NULL,
 CHECK(NOT (convite AND convite_dispensado)));
ALTER TABLE onboarding_progresso ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON onboarding_progresso FROM anon,authenticated;
