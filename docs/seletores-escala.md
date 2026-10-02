# Seletores de escala — T05

GET /voluntarios/opcoes?nome=&tipo=&pagina=0: projeção de ID, nome, tipo, ativo e funções habilitadas; somente ativos. Busca pelo início do nome, sem diferença entre maiúsculas/minúsculas, preservando acentos. %, _ e barra são literais. Páginas de 30, consulta 31 para temMais, ordenação nome/UUID sem count adicional. Não carrega fotos, contatos, endereço ou nascimento.

POST /voluntarios/opcoes/ids: até 100 UUIDs por pedido para nomes/funções de alocações existentes, incluindo inativos já alocados. É leitura projetada, não concessão de permissão nem mutação comercial; exige PESSOA ou ESCALA ou VAGA. IDs inexistentes/externos não são devolvidos. Contexto tenant é obrigatório em ambas as consultas. V073–V074 criam índices de tenant/nome prefixo e tenant/ativo. Limite de busca 160; páginas negativas/excessivas são rejeitadas.

Montador usa primeira página e resolve apenas IDs alocados e irmãos diretos conhecidos no apoio. Seletor fechado não consulta; aberto faz busca remota com debounce de 300 ms, tipo e paginação. Trocar busca/fechar cancela consulta. Seleção entrega a projeção antes de alterar vaga. Palette de arraste também busca páginas remotas; resposta antiga não substitui resultado atual. O painel de não escalados identifica que considera somente pessoas carregadas, sem afirmar que todos da paróquia já foram alocados.

Limites restantes do backlog amplo: apoio mensal ainda traz metadados de disponibilidade/irmãos do escopo existente; outros consumidores legados de list()/active() permanecem fora desta revisão. Índices/paginação não substituem benchmark representativo com o volume de produção. Não há seleção automática nem IA.

## Painel e grade sem lista completa (02/10/2026)

O início não carrega mais a lista de voluntários só para contar: GET /voluntarios/painel (PESSOA, ESCALA ou VAGA) devolve ativos, coroinhas e acólitos (AMBOS conta nos dois), MESC e mandatos que vencem em até 90 dias (inclusive vencidos, data de Brasília), tudo por contagem no banco. A grade mensal de indisponibilidades usa a projeção leve paginada de `/voluntarios/opcoes` em páginas de 30 (até 60 páginas), sem ficha completa nem fotos. Os consumidores legados de `list()`/`active()` foram retirados dessas duas telas.
