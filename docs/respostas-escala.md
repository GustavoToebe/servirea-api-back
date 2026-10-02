# Confirmação e recusa de participação em escala

Entrega local de 02/10/2026, migration V062. Sem IA e sem implantação em produção.

## Operação

Meus compromissos mostra situação Pendente/Confirmada/Recusada nas vagas atribuídas à própria pessoa em escalas FINALIZADAS. Com a permissão PORTAL_RESPONDER, pode confirmar, recusar ou mudar a decisão até o início da celebração. Prazo é o horário de início convertido de Brasília para Instant UTC e conferido no servidor. Não há configuração de prazo antecipado nesta versão. Eventos gerais não recebem ações de resposta.

Recusar não esvazia a vaga nem registra falta. A coordenação providencia a substituição pelo fluxo de reabertura/alocação já existente. Intenção de participar e presença realizada são estados distintos. Nenhuma mensagem de WhatsApp/e-mail é enviada nesta operação.

Na montagem de uma escala, Respostas de participação abre /escalas/{id}/respostas para usuários com VAGA_RESPOSTA_LER. Lista paginada de 30 alocações, com filtro de resposta, pessoa/nome, celebração, início, função e horário da decisão. Não inclui vagas livres nem linhas de referência. Escalas em rascunho/canceladas continuam consultáveis pela coordenação; a lista descreve alocações atuais, não um relatório de presença.

## Identidade e autorização

JWT identifica tenant/usuário; UsuarioTenant.pessoaId resolve exclusivamente a própria pessoa. Recurso comercial PORTAL_VOLUNTARIO precisa estar explícito nos direitos para leitura pessoal e resposta. Configure PORTAL_VOLUNTARIO + PORTAL_RESPONDER no perfil do voluntário; não conceda VAGA_ALOCAR ou acesso geral a pessoas para fazê-lo responder. Coordenação exige ação VAGA_RESPOSTA_LER do módulo VAGA; consulta histórica não exige recurso comercial de escrita. Acesso total acompanha os novos códigos. Perfis limitados precisam de configuração e nova sessão.

Não há funcionalidade comercial nova: a resposta faz parte do portal existente. Não foram alterados planos, perfis, vínculos ou contratos reais. Conta de suporte não pode responder como uma pessoa. Tentar responder vaga de outra pessoa/paróquia, rascunho, cancelamento ou referência retorna 404 genérico. Sem recurso, 403; sem permissão, 403; prazo/versão desatualizada, 409. Nenhum pessoaId/tenantId/usuarioId vem do corpo da resposta.

## Concorrência e histórico

PUT /portal/vagas/{vagaId}/resposta recebe {resposta: CONFIRMADA|RECUSADA, versao: número}. PENDENTE não é decisão válida. Versão obrigatória é a versão da vaga recebida no portal; o servidor devolve resposta, respondidoEm e versao. Não repetir escrita automaticamente após 409: recarregar e decidir novamente. Repetir a mesma decisão com a versão atual não acrescenta histórico. Reenvio com versão anterior após gravação recebe 409.

Lock da paróquia serializa resposta com alteração do vínculo pessoal. Lock da escala serializa com reabertura, cancelamento, exclusão e alocação. @Version na vaga protege também alterações concorrentes de presença. Ordem para responder: paróquia → escala → vaga. Rotinas de alocação/edição usam lock da escala; não adicionar lock de paróquia depois dele. A sessão tenant-aware é aberta com contexto do JWT.

Reabrir a escala limpa respostas de suas vagas para PENDENTE. Trocar/esvaziar pessoa também limpa a resposta; repetir a mesma alocação não altera a decisão. Edição completa já recria eventos/vagas, portanto exige novas respostas nos novos IDs. Nenhum aceite anterior é aplicado a outra pessoa ou a uma vaga recriada.

GET /portal/vagas/{vagaId}/respostas?pagina=0 devolve histórico paginado de 30 da pessoa atualmente associada à conta, mesmo se a vaga foi removida. IDs sem histórico próprio retornam lista vazia, sem revelar decisões de terceiros. Interface abre histórico de compromissos atuais; histórico de vagas excluídas permanece consultável pelo endpoint com o ID conhecido e na auditoria, sem navegador de arquivos antigos nesta versão.

Tabela escala_resposta_historico armazena UUIDs de escala/vaga/pessoa/usuário, decisão, horário e versão; referências são históricas como audit_log, sem cascata de exclusão do domínio. Não guarda nome, motivo, contato ou cuidado. @TenantId, índice composto, RLS sem policies e revogação anon/authenticated. Mudança real grava histórico e auditoria na mesma transação; rollback não deixa decisão parcial.

GET /escalas/{id}/respostas?pagina=0&resposta=RECUSADA consulta alocações atuais da coordenação. Filtro opcional PENDENTE/CONFIRMADA/RECUSADA. Portal recebe vagaId, resposta, versao, respondidoEm e prazoResposta nos compromissos de escala; eventos retornam null nesses campos. Arquivo ICS continua sendo agenda da alocação: recusar não remove o evento automaticamente.

## Verificação e próximos passos

Testes HTTP com JWT real cobrem decisão, versão, repetição, prazo, outra pessoa/paróquia, plano/permissão, reabertura, troca, cancelamento, histórico após exclusão e duas respostas simultâneas. Frontend verifica versão no request, permissão/prazo, conflito sem retry, consulta de coordenação e cancelamento de histórico antigo. Comandos no AGENTS.md; números finais no registro de implementação.

Permanecem pendentes candidatura a vagas, trocas com aprovação, responsáveis/dependentes, notificações, prazo antecipado configurável e relatórios de histórico da coordenação. Não foram implementados junto desta decisão pessoal.

## Evolução V063

Candidatura com aprovação agora entregue em [candidaturas-vagas.md](candidaturas-vagas.md). Referências anteriores a candidatura pendente descrevem a V062; trocas/dependentes/notificações permanecem no backlog.
