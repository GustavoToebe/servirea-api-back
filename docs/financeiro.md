# Financeiro paroquial simples

O módulo `/financeiro` pertence ao Servirea. Organiza dinheiro da paróquia; assinaturas e cobranças do SaaS continuam na Central.

## Uso

1. Em **Perfis**, habilite **Financeiro paroquial** para quem pode consultar. Conceda separadamente criar lançamentos, editar/cancelar, baixar/estornar e gerenciar contas/categorias. Perfis com permissões específicas precisam de liberação explícita. Perfis de acesso total já incluem o catálogo completo, inclusive o financeiro.
2. Em **Contas / bancos**, cadastre Caixa, Banco etc. e o saldo existente antes das primeiras baixas. Uma conta pode começar com saldo negativo. Depois do primeiro lançamento, saldo e data inicial ficam protegidos.
3. Monte o **Plano de contas** (V083, antes "Categorias"): crie **grupos** de saídas (ex.: Despesas fixas) e de entradas (ex.: Doações) e, dentro de cada grupo, as **contas contábeis** (Energia elétrica, Dízimo). Só a conta contábil recebe lançamento; o grupo serve para organizar e define o tipo (entrada/saída), que a conta herda. Nomes não se repetem entre os grupos de um tipo nem entre as contas de um grupo.
4. Crie entradas ou saídas pendentes com descrição, valor, vencimento, conta/banco e **conta contábil** do mesmo tipo do lançamento. Ao confirmar que o dinheiro foi recebido ou pago, use **Dar baixa** e informe a data real.
5. Para corrigir baixa, use **Estornar**. O lançamento volta a pendente; então pode ser editado ou cancelado. Não há exclusão do histórico. Contas e categorias usadas podem ser inativadas.

## Cálculos

- Lista: filtros por **vencimento**, descrição, tipo, situação, conta e categoria; páginas de 30, máximo de 100 na API.
- Resumo do período: somente lançamentos pagos/recebidos cuja **data da baixa** está no período. Resultado = receitas − despesas.
- Saldo por conta até a data final = saldo inicial + receitas recebidas acumuladas − despesas pagas acumuladas. Não usa apenas os lançamentos visíveis na página e não inclui pendentes/cancelados. Conta inativa continua no histórico e no saldo.
- Datas usam o calendário de São Paulo; baixa futura ou anterior ao saldo inicial é recusada. Valores positivos com até duas casas decimais; tipo define entrada ou saída.

## Plano de contas

`financeiro_categoria` guarda os dois níveis: sem `grupo_id` é grupo, com `grupo_id` é conta contábil (só dois níveis; chave estrangeira composta com a paróquia). Regras da API: a conta contábil precisa do mesmo tipo do grupo; o grupo não vira conta (nem a conta vira grupo com lançamentos); o tipo não muda enquanto houver contas ou lançamentos; lançamento recusa grupo, tipo diferente e conta ou grupo inativos; criar conta em grupo inativo é recusado. A V083 converteu as categorias antigas em contas contábeis dentro de "Saídas (migradas)" ou "Entradas (migradas)", com o tipo vindo dos lançamentos (saída quando misto ou nunca usada). A tela abre direto em `/financeiro?aba=plano-de-contas`. Não há rateio, centro de custo nem contabilidade fiscal.

## Proteções

Tenant vem da sessão, nunca do formulário. Consultas JPA usam `@TenantId`; chaves estrangeiras compostas impedem ligar conta/categoria de outra paróquia. RLS ativada e acesso direto de anon/authenticated revogado. Toda mutação gera auditoria sem descrição ou observações sensíveis.

Baixa/estorno/cancelamento travam o lançamento e conferem a versão enviada pela tela. Duas baixas concorrentes resultam em um sucesso e um conflito; o saldo não duplica. Saldo é calculado dos lançamentos, sem contador mutável. A conta é travada ao criar lançamento e ao editar saldo inicial.

## Entrega e limites

Migration aditiva **V054**, após V053. Testada em PostgreSQL 17 descartável e incluída no `schema.sql` gerado. Aplicação em produção ocorre pelo Flyway durante o deploy da versão; esta implementação não executou o deploy.

Escopo manual: sem conciliação bancária, conexão com bancos, emissão fiscal, contabilidade de partidas dobradas ou processamento de pagamentos. Não registrar números completos de cartões, senhas bancárias ou segredos em observações.

## Conta bancária (V084)

A conta (ou caixa) guarda também: tipo (`CORRENTE`, `POUPANCA`, `CAIXA`, `OUTRA`), banco, agência, número da conta, titular, abertura, encerramento e até 10 chaves PIX (`CPF`, `CNPJ`, `EMAIL`, `TELEFONE`, `ALEATORIA`), no máximo uma principal. Em conta corrente ou poupança, banco, agência, conta e titular são obrigatórios; caixa e `OUTRA` (tipo das contas anteriores à V084) não exigem. O encerramento não pode ser anterior à abertura. As chaves são normalizadas (CPF/CNPJ/telefone só dígitos, e-mail em minúsculas), não podem repetir e ficam numa coluna JSON da própria conta. Nenhuma chave é validada junto ao banco: o sistema só as guarda para consulta. Sem integração bancária e sem Pix automático.
