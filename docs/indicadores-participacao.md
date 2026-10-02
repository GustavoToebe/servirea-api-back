# Indicadores privados de participação — F25

GET /indicadores/participacao?de=AAAA-MM-DD&ate=AAAA-MM-DD&busca=&pagina=0. Período de no máximo 366 dias, busca literal até 160 caracteres, páginas de 30, ordem alfabética/UUID. Exige simultaneamente INDICADORES e ESCALA. Não há rota pública, ranking, pontuação ou reconhecimento automático.

Conta vagas ocupadas de escalas FINALIZADA, excluindo linhas de referência e rascunhos. Por pessoa: alocações, presentes, faltas, presença pendente, confirmações e recusas. Resumo corresponde ao período/busca completos; total é quantidade de pessoas, não vagas. Uma pessoa em várias funções/datas conta várias alocações. Confirmação não prova presença, e presença pendente pode corresponder ao futuro. Esta entrega não mede participação nos eventos gerais nem todas as atividades da paróquia.

Projeções agregadas em transação de leitura REPEATABLE_READ, com tenant aplicado pelo Hibernate. Sem contatos, fotos, nascimento ou observações pessoais. Tela descarta resultados anteriores ao buscar novamente e mostra erro em vez de zero fictício. Indicadores integram o contexto de escalas; não foi criado adicional comercial separado para contagens privadas.

Homologar período inválido, ausência de dados, pessoa em múltiplas vagas, presenças/recusas, duas paróquias e ausência de uma das permissões. Não exporta lista pública nem aciona mensagens.
