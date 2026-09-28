-- CPF único por paróquia (28/09/2026). Vazio não conta. Antes do deploy, conferir em produção:
-- SELECT tenant_id, cpf, count(*) FROM pessoa WHERE cpf IS NOT NULL AND cpf <> '' GROUP BY 1,2 HAVING count(*) > 1;
CREATE UNIQUE INDEX ux_pessoa_tenant_cpf ON pessoa (tenant_id, cpf) WHERE cpf IS NOT NULL AND cpf <> '';
