-- Cuidado e acolhimento (28/09/2026): condição que pede cuidado, opcional. Dado de saúde (LGPD art. 11).
CREATE TYPE condicao_especial AS ENUM ('SINDROME_DOWN','TEA','TDAH','ANSIEDADE','DEPRESSAO','BORDERLINE','OUTRA');
ALTER TABLE pessoa
  ADD COLUMN condicoes condicao_especial[] NOT NULL DEFAULT '{}',
  ADD COLUMN nivel_suporte_tea integer CHECK (nivel_suporte_tea BETWEEN 1 AND 3),
  ADD COLUMN condicao_outra varchar(200),
  ADD COLUMN cuidados varchar(1000);
ALTER TABLE inscricoes
  ADD COLUMN condicoes condicao_especial[] NOT NULL DEFAULT '{}',
  ADD COLUMN nivel_suporte_tea integer CHECK (nivel_suporte_tea BETWEEN 1 AND 3),
  ADD COLUMN condicao_outra varchar(200),
  ADD COLUMN cuidados varchar(1000),
  ADD COLUMN consentimento_cuidados_em timestamptz;
