# Responsáveis e coordenação própria — F01/F12

## Responsáveis

O cadastro de parentesco não concede acesso. A administração deve conferir a identidade, vincular a conta à pessoa em Usuários → Vínculos e liberar o perfil com PORTAL_VOLUNTARIO e PORTAL_DEPENDENTES. Em Pessoas → ficha do responsável → Autorizações de acesso, habilitar consulta e, separadamente, resposta. PORTAL_RESPONDER também é necessário para confirmar/recusar.

O portal de dependentes mostra apenas compromissos publicados/finalizados da pessoa expressamente autorizada. Não abre sua ficha, contatos, cuidados, calendário privado, trocas, candidaturas ou histórico pessoal. Responder mantém a alocação; não equivale a presença. O histórico de resposta guarda a pessoa dependente e a conta real do responsável.

GET /portal/dependentes?pagina=0 retorna até 30 pessoas (ID, nome e podeResponder). GET /portal/dependentes/{id}/compromissos?de=&ate= aceita até 366 dias e 500 compromissos. PUT /portal/dependentes/{pessoa}/vagas/{vaga}/resposta recebe resposta e versão da vaga.

GET /pessoas/{responsavel}/acessos-dependentes?pagina=0 exige PESSOA. PUT .../{dependente} exige PESSOA_ALTERAR, plano PORTAL_VOLUNTARIO e {consulta,resposta,versao}. Responder exige consultar. Versão antiga gera 409. Revogação e resposta usam o mesmo lock da paróquia; a autorização é revalidada no servidor em cada ação. Retirar/recriar a relação familiar limpa a autorização, exigindo nova conferência.

## Coordenação

Minhas pastorais exige plano PASTORAIS, conta pessoal explicitamente vinculada e permissão PASTORAL_COORDENACAO. Somente equipes ativas em que essa pessoa é membro ativo COORDENADOR são visíveis. Não concede o módulo administrativo PASTORAL nem busca geral de pessoas.

GET /pastorais/minhas-equipes?pagina=0 lista até 30 equipes; GET .../{id}/membros?pagina=0 lista participantes em páginas de 30. PUT .../{id}/membros/{pessoa} recebe {ativo,versao} e exige PASTORAL_COORDENACAO_GERENCIAR. Pode inativar/reativar participantes existentes. Nomeação, remoção de coordenadores, inclusão de pessoas e cadastro da equipe ficam com a administração. Versão antiga gera 409; equipe não autorizada gera 404. Alterações e revogação de coordenação compartilham o lock da equipe.

## Operação e limites

Suporte não substitui identidade pessoal. Nenhuma autorização é inferida por nome, e-mail, parentesco ou cargo textual. Os perfis existentes precisam dos novos códigos explicitamente; ADMIN tem o catálogo completo. V076 acrescenta consulta/resposta/versão à relação existente, inicialmente false, mantendo RLS e isolamento por tenant. Nenhum dado de produção foi alterado nesta rodada.

Teste manual: criar responsável e dependente fictícios, conferir vínculo pessoal, liberar somente consulta, depois resposta, revogar e repetir. Criar duas equipes, vincular coordenador a apenas uma, conferir que a outra não aparece e que não pode alterar coordenadores. Testar perfil sem os novos códigos e retirar vínculo ativo durante a sessão.
