# Listas paginadas

## Contrato

`GET /pessoas/pagina`, `GET /inscricoes/pagina` e `GET /eventos/pagina` retornam `{itens,pagina,tamanho,total,paginas}`. Página começa em zero; tamanho padrão 30, de 1 a 100. Página negativa, acima de 100000 ou tamanho inválido retorna 400. Página além do total retorna itens vazios. Ordenação tem desempate por id; paginação por offset pode deslocar registros se houver inclusões/alterações entre requisições.

Pessoas aceita papel, nome/número, tipo e ativo; inscrições aceita status. Filtros são aplicados antes do limite. Pessoas ordena nome/id; inscrições createdAt/id decrescentes. DTOs são montados dentro da transação e respeitam PESSOA_CUIDADOS_LER.

`GET /pessoas/resumo` calcula pessoas, voluntários ativos/inativos e inscrições pendentes no banco, sem carregar fichas. Pendentes fica zero sem PERM_INSCRICAO. `GET /pessoas/opcoes` aceita papel, nome/número e limite (padrão 30, máximo 100); retorna apenas id, sequencial, nome e indicadores de papel. O endpoint já existe, mas a conversão dos seletores antigos ainda está pendente.

Eventos ordena globalmente próximos por início crescente, seguidos de passados/cancelados por início decrescente, com desempate por id. Rascunhos permanecem próximos conforme a regra vigente. Um snapshot de leitura agrupa contagens das inscrições e busca fotos dos eventos da página em lote. Cada evento tem até dez fotos; a capa explícita tem preferência, depois a mais antiga. A conexão do banco é liberada antes de solicitar URLs ao Storage. Falha de assinatura mantém o comportamento de erro da lista; detalhe/upload de evento ainda precisam de revisão de suas chamadas externas.

## Interface

Pessoas, inscrições/histórico e eventos carregam páginas de 30 registros. Busca/filtro reinicia em zero; troca de página limpa a seleção de pessoas. Listagens para impressão e comunicados usam os marcados da página atual, explicitados na tela. Pessoas usa OnPush e marca alterações após chamadas assíncronas; respostas antigas de carga são descartadas.

As rotas antigas sem paginação permanecem por compatibilidade com seletores e consumidores existentes. Portanto a redução do carregamento integral está concluída nessas telas, mas não em todos os seletores nem em todos os clientes da API.

## Verificação

Testes HTTP cobrem limite, filtro, permissão, tenant, ausência de cuidados e páginas sem repetição em conjunto estático. Eventos verifica ordenação antes do limite, contagem correta e assinatura de foto sem transação ativa. Testes de interface cobrem navegação/filtros/seleção. Não há ensaio de carga de grande volume concluído.
