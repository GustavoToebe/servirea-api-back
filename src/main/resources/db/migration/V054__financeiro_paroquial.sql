-- Financeiro paroquial simples. Separado da cobrança comercial da Central.
CREATE TABLE financeiro_conta (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
 nome varchar(120) NOT NULL, saldo_inicial numeric(14,2) NOT NULL DEFAULT 0, data_saldo_inicial date NOT NULL,
 ativo boolean NOT NULL DEFAULT true, UNIQUE(tenant_id,id));
CREATE UNIQUE INDEX ux_financeiro_conta_nome ON financeiro_conta(tenant_id,lower(nome));
CREATE TABLE financeiro_categoria (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
 nome varchar(120) NOT NULL, ativo boolean NOT NULL DEFAULT true, UNIQUE(tenant_id,id));
CREATE UNIQUE INDEX ux_financeiro_categoria_nome ON financeiro_categoria(tenant_id,lower(nome));
CREATE TABLE financeiro_movimento (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
 versao bigint NOT NULL DEFAULT 0, descricao varchar(200) NOT NULL, tipo varchar(20) NOT NULL CHECK(tipo IN ('RECEITA','DESPESA')),
 situacao varchar(20) NOT NULL DEFAULT 'PENDENTE' CHECK(situacao IN ('PENDENTE','PAGO','CANCELADO')),
 valor numeric(14,2) NOT NULL CHECK(valor>0), vencimento date NOT NULL, data_pagamento date,
 conta_id uuid NOT NULL, categoria_id uuid NOT NULL, observacoes varchar(1000), criado_em timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(tenant_id,conta_id) REFERENCES financeiro_conta(tenant_id,id),
 FOREIGN KEY(tenant_id,categoria_id) REFERENCES financeiro_categoria(tenant_id,id),
 CHECK ((situacao='PAGO') = (data_pagamento IS NOT NULL)));
CREATE INDEX ix_financeiro_movimento_vencimento ON financeiro_movimento(tenant_id,vencimento,id);
CREATE INDEX ix_financeiro_movimento_baixa ON financeiro_movimento(tenant_id,data_pagamento,conta_id) WHERE situacao='PAGO';
ALTER TABLE financeiro_conta ENABLE ROW LEVEL SECURITY;
ALTER TABLE financeiro_categoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE financeiro_movimento ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON financeiro_conta,financeiro_categoria,financeiro_movimento FROM anon,authenticated;
