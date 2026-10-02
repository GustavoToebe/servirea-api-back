ALTER TABLE usuario
    ADD COLUMN mfa_segredo text,
    ADD COLUMN mfa_pendente text,
    ADD COLUMN mfa_pendente_ate timestamptz,
    ADD COLUMN mfa_ultimo_passo bigint NOT NULL DEFAULT -1,
    ADD COLUMN credenciais_versao bigint NOT NULL DEFAULT 0;

CREATE TABLE usuario_mfa_recuperacao (
    usuario_id uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    codigo_hash varchar(64) NOT NULL,
    usado_em timestamptz,
    PRIMARY KEY (usuario_id, codigo_hash)
);
ALTER TABLE usuario_mfa_recuperacao ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON usuario_mfa_recuperacao FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON usuario_mfa_recuperacao FROM authenticated;
    END IF;
END $$;
