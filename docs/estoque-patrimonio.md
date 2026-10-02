# Estoque e patrimônio simples — F22

V072. Cadastro de item consumível ou patrimônio, código/etiqueta único por paróquia, nome, unidade, local, responsável opcional vinculado à paróquia e situação. Consumíveis aceitam até 3 casas decimais; patrimônio usa quantidade inteira. Itens começam com saldo zero. Entradas, saídas e ajuste de inventário são a única forma de modificar saldo. Ajuste recebe o saldo contado, não uma diferença. Não há estoque negativo.

GET /estoque e /estoque/{id}/movimentos: páginas de 30, filtros literais, ordem estável. GET /estoque/{id} e /estoque/responsaveis (até 30 contas ativas por busca). POST /estoque e PUT /estoque/{id}: versão lida, permissão ESTOQUE + ESTOQUE_EDITAR. POST /estoque/{id}/movimentos exige ESTOQUE + ESTOQUE_MOVIMENTAR; AJUSTE exige também ESTOQUE_AJUSTAR. Mutações exigem plano ESTOQUE; leitura histórica permanece disponível com autorização.

Movimento possui versão do item, chave UUID gerada uma vez no formulário, tipo, quantidade, motivo e responsável. Histórico imutável registra antes/depois, instante e autor quando disponível. Repetir mesma chave/conteúdo retorna o movimento existente sem reaplicar saldo, mesmo com versão anterior. Outra carga para mesma chave retorna 409. Em falha de rede, a tela congela o pedido e repete exatamente a chave/carga; ao fechar, recomenda conferir histórico antes de iniciar outro pedido. Não existe estorno automático nem exclusão: correções são novos movimentos explicados.

Raiz da paróquia travada antes de ler o item, versão otimista e unicidade SQL da chave. Essa solução serializa escritas da paróquia, suficiente para o módulo simples; medir carga antes de substituir por locks mais granulares. Saldo/movimento/auditoria são atômicos. AtualizadoEm faz um ajuste sem diferença de saldo avançar versão. FK do responsável é do vínculo local. Nomes exibidos são resolvidos em lote por página apenas entre vínculos da própria paróquia; vínculo removido vira indisponível, sem consulta global de contas. Não mantém snapshot histórico dos nomes.

Tipo e unidade não mudam após primeiro movimento; item inativo não recebe novos movimentos. Chave e histórico não são removidos junto ao cadastro. Tabelas @TenantId, RLS e revogação de anon/authenticated. Sem avaliação monetária, depreciação, compras, empréstimos, manutenção ou lançamento no financeiro.

Homologar duas saídas simultâneas, repetição após erro, estoque insuficiente, ajuste zero, permissão de ajuste, responsável de outra paróquia, código duplicado, patrimônio fracionado, atualização obsoleta e tela pequena.
