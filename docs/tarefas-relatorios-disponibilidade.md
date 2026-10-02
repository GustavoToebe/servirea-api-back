## Responsáveis em tarefas — F21

Em Tarefas, criar/editar oferece equipe textual e uma conta responsável opcional. O seletor consulta somente id/nome de até 30 vínculos ativos da paróquia; busque pelo nome para encontrar outros. É possível remover a atribuição. Conta inativa já atribuída permanece identificada no histórico, mas não pode receber nova atribuição. Excluir fisicamente o vínculo limpa somente o responsável, preservando a tarefa e o tenant.

Filtros por título, situação, responsável e Minhas tarefas são aplicados pelo servidor com páginas de 30. Minhas tarefas usa a identidade do JWT e exige conta pessoal; acesso de suporte não representa um responsável. Atribuir não concede permissões e não envia notificações. Ler exige TAREFA; criar/editar exige TAREFA_CRIAR/ALTERAR e recurso comercial TAREFAS. Comentários e fluxos especializados permanecem evolução.

## Participação e CSV — F17

Em Relatórios, abra Participação e CSV. Filtre período, pessoa, função, presença e resposta. A API projeta somente nomes, escala, celebração, data/horário, função e estados; não devolve ficha, contatos ou cuidados. Considera vagas ocupadas de escalas FINALIZADAS e ignora referências/rascunhos. Não mistura confirmação de intenção com presença registrada.

Consulta paginada de 30; período até 366 dias. Buscar aplica os filtros; paginação/exportação usam esse filtro aplicado, mesmo se o formulário for alterado antes de nova busca. CSV exporta todas as linhas filtradas, até 5.000, independentemente da página. Acima do limite, pede reduzir os filtros, sem entregar arquivo truncado.

Permissões: AUDITORIA para consulta; AUDITORIA + RELATORIO_EXPORTAR para CSV. A permissão nova aparece em Auditoria e relatórios do catálogo de perfis; perfil personalizado precisa de concessão explícita. Exportação auditada, cache desabilitado, UTF-8 com BOM, separador ponto e vírgula, aspas duplicadas e fórmulas =/+/−/@ neutralizadas inclusive após espaços/controles. A coluna e o valor negativo usam o hífen ASCII no código. O arquivo contém nomes; manter compartilhamento restrito à equipe autorizada. Relatório existente e impressão PDF/PNG continuam disponíveis.

## Minha indisponibilidade — F02

No portal, abra Minha indisponibilidade, escolha o mês e adicione datas/períodos em que não pode servir. Dia inteiro bloqueia todos os períodos; também é possível informar períodos distintos do mesmo dia, sem duplicação. Sem restrição limpa as datas após confirmação. Sem datas e sem essa marcação significa pendente.

Requer conta pessoal com vínculo explícito a voluntário ativo da paróquia, recurso PORTAL_VOLUNTARIO e PORTAL_DISPONIBILIDADE para salvar. O corpo não aceita escolher pessoa ou paróquia. Pode salvar o mês atual ou os próximos 12; o servidor usa Brasília. Leitura de meses anteriores permite consulta. Datas negativas entram no apoio à montagem e na elegibilidade de candidaturas/trocas. Salvar não remove alocações já publicadas nem responde participação automaticamente.

Coordenação e portal compartilham versão mensal. Se alguém salvar após sua consulta, recebe 409 e mantém suas alterações na tela; Recarregar pede confirmação para descartar e consultar o mês atual. A grade administrativa conserva períodos múltiplos/observações não editados e mostra períodos combinados; sua visualização continua focada nos fins de semana. Datas de dias úteis cadastradas no portal também são preservadas e entram na elegibilidade. Marcar uma célula substitui explicitamente os períodos dessa data.


V065: tarefa.responsavel_usuario_id e indisponibilidade_mes. Atualizar API/frontend juntos; contrato mensal exige versao. Testes e comandos no [índice](README.md).
