CREATE TABLE login_tentativa(
 chave varchar(100) PRIMARY KEY,
 janela_ate timestamptz NOT NULL,
 tentativas integer NOT NULL CHECK(tentativas>=1));
CREATE INDEX login_tentativa_janela_idx ON login_tentativa(janela_ate);
ALTER TABLE login_tentativa ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON login_tentativa FROM anon,authenticated;
