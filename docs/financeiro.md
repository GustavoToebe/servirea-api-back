# Financeiro paroquial simples

O módulo `/financeiro` pertence ao Servirea. Organiza dinheiro da paróquia; assinaturas e cobranças do SaaS continuam na Central.

## Uso

1. Em **Perfis**, habilite **Financeiro paroquial** para quem pode consultar. Conceda separadamente criar lançamentos, editar/cancelar, baixar/estornar e gerenciar contas/categorias. Perfis com permissões específicas precisam de liberação explícita. Perfis de acesso total já incluem o catálogo completo, inclusive o financeiro.
2. Em **Contas / bancos**, cadastre Caixa, Banco etc. e o saldo existente antes das primeiras baixas. Uma conta pode começar com saldo negativo. Depois do primeiro lançamento, saldo e data inicial ficam protegidos.
3. Cadastre categorias, como doações, dízimo, manutenção e contas de consumo.
4. Crie entradas ou saídas pendentes com descrição, valor, vencimento, conta e categoria. Ao confirmar que o dinheiro foi recebido ou pago, use **Dar baixa** e informe a data real.
5. Para corrigir baixa, use **Estornar**. O lançamento volta a pendente; então pode ser editado ou cancelado. Não há exclusão do histórico. Contas e categorias usadas podem ser inativadas.

## Cálculos

- Lista: filtros por **vencimento**, descrição, tipo, situação, conta e categoria; páginas de 30, máximo de 100 na API.
- Resumo do período: somente lançamentos pagos/recebidos cuja **data da baixa** está no período. Resultado = receitas − despesas.
- Saldo por conta até a data final = saldo inicial + receitas recebidas acumuladas − despesas pagas acumuladas. Não usa apenas os lançamentos visíveis na página e não inclui pendentes/cancelados. Conta inativa continua no histórico e no saldo.
- Datas usam o calendário de São Paulo; baixa futura ou anterior ao saldo inicial é recusada. Valores positivos com até duas casas decimais; tipo define entrada ou saída.

## Proteções

Tenant vem da sessão, nunca do formulário. Consultas JPA usam `@TenantId`; chaves estrangeiras compostas impedem ligar conta/categoria de outra paróquia. RLS ativada e acesso direto de anon/authenticated revogado. Toda mutação gera auditoria sem descrição ou observações sensíveis.

Baixa/estorno/cancelamento travam o lançamento e conferem a versão enviada pela tela. Duas baixas concorrentes resultam em um sucesso e um conflito; o saldo não duplica. Saldo é calculado dos lançamentos, sem contador mutável. A conta é travada ao criar lançamento e ao editar saldo inicial.

## Entrega e limites

Migration aditiva **V054**, após V053. Testada em PostgreSQL 17 descartável e incluída no `schema.sql` gerado. Aplicação em produção ocorre pelo Flyway durante o deploy da versão; esta implementação não executou o deploy.

Escopo manual: sem conciliação bancária, conexão com bancos, emissão fiscal, contabilidade de partidas dobradas ou processamento de pagamentos. Não registrar números completos de cartões, senhas bancárias ou segredos em observações.
