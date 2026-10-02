# Portal pessoal, calendário e pastorais

Implementação local de 02/10/2026. Servirea V061. Sem IA; este documento não confirma implantação.

## Configurar antes do uso

1. Na Central, consulte o catálogo de recursos do aplicativo e cadastre PASTORAIS, PORTAL_VOLUNTARIO e CALENDARIO como funcionalidades. Inclua explicitamente os códigos nos planos desejados e envie os direitos. Nenhum plano ou contrato real foi alterado nesta execução.
2. Configure os perfis: PASTORAL para consulta; PASTORAL_CRIAR, PASTORAL_ALTERAR e PASTORAL_GERENCIAR para as respectivas ações; PORTAL_VOLUNTARIO e CALENDARIO para a consulta pessoal e gestão da própria assinatura. Acesso total acompanha o catálogo; perfis limitados precisam receber os códigos. Coordenador de pastoral é um papel do cadastro, não concede permissões automaticamente.
3. Em Usuários e pessoas (/usuarios/vinculos), um administrador com USUARIO_ALTERAR associa explicitamente a conta à pessoa cadastrada. Uma pessoa só pode ter uma conta vinculada em cada paróquia. Não há inferência por nome, CPF ou e-mail. Alterar/remover vínculo revoga o calendário anterior. A restrição de concessão de acesso total também vale para alterar o vínculo de administradores.
4. Aplique a V061 no fluxo normal de release, após backup e homologação em ambiente apropriado. Atualize backend e frontend juntos. Reentre na sessão após configurar permissões.

## Portal de consulta

Menu Meus compromissos, rota /portal. GET /portal/compromissos?de=AAAA-MM-DD&ate=AAAA-MM-DD usa exclusivamente a pessoa associada ao usuário do JWT e à paróquia selecionada. Não recebe pessoaId do cliente. Recurso PORTAL_VOLUNTARIO obrigatório também para leitura; não existe histórico de terceiros a preservar neste portal novo. Conta de suporte não representa uma pessoa.

Lista vagas atribuídas em escalas FINALIZADAS, excluindo linhas de referência, e eventos PUBLICADOS em que a pessoa está inscrita. Não mostra rascunhos, outros participantes, contatos ou cuidados. Sem vínculo, retorna vinculado=false e lista vazia. Período de até 366 dias, no máximo 500 compromissos; volume maior pede reduzir o período. Horários do domínio são de Brasília. Não há confirmação/recusa, candidatura, troca, dependentes ou edição de cadastro nesta versão.

## Calendário privado e revogável

No portal, Gerar link cria/rotaciona a assinatura individual; Revogar bloqueia novas consultas. POST /calendario/assinatura exige CALENDARIO e PORTAL_VOLUNTARIO no plano e no perfil. DELETE na mesma rota permite revogar após downgrade, mantendo as permissões necessárias. O token é devolvido uma única vez; frontend conserva somente em memória e limpa ao sair da tela. Banco guarda SHA-256 do token aleatório de 256 bits, com validade de 365 dias. Existe uma assinatura por usuário/paróquia.

GET /public/calendario/{token}.ics é autenticado pela posse do link secreto, sem JWT. Revalida conta/paróquia, vínculo, perfil e recursos a cada consulta. Token inválido, expirado, revogado, conta inativa, perda de permissão ou downgrade tornam o feed indisponível (404). Identidade global resolve o tenant antes de abrir TransactionTemplate de leitura; tabelas de domínio usam @TenantId. O contexto anterior é restaurado ao concluir.

Feed pessoal contém janela móvel de 30 dias passados e 335 futuros, CLASS:PRIVATE, datas UTC convertidas de Brasília, escape de texto, CRLF e dobramento de linhas em UTF-8 conforme [RFC 5545](https://www.rfc-editor.org/rfc/rfc5545). Escalas e eventos sem término usam duração estimada de uma hora, sem alterar o cadastro original. UID estável por compromisso. Resposta no-store, no-referrer e noindex. Sem lista pública geral da paróquia.

Use HTTPS em ambiente publicado. O link funciona como credencial: não compartilhar, registrar em logs ou enviar à Central. Configure proxy/monitoramento para mascarar o segmento secreto de /public/calendario/. Copiar o link grava a credencial na área de transferência do usuário. Rotação/revogação não apaga cópias já importadas ou cache de aplicativos; atualização depende do cliente. Consulta já em andamento pode concluir. Acima de 500 compromissos a consulta falha, sem truncar silenciosamente.

## Pastorais e equipes simples

Menu Pastorais, rota /pastorais. Nome até 120 caracteres, descrição em texto simples até 1.000 e situação ativa/inativa. Lista e membros paginados de 30 em 30, busca literal por nome. Adicione pessoas já cadastradas usando busca explícita de no máximo 30 opções; resposta contém somente id/nome. Não há criação automática de pessoa ou vínculo com conta.

Papéis MEMBRO e COORDENADOR, com ativação/inativação e controle de versão. Pessoa não se repete na mesma equipe; pode participar de equipes distintas. Equipe inativa conserva membros e impede novas alterações de associação até reativar. Sem exclusão física de histórico. Mutação exige PASTORAIS; consulta histórica segue a permissão PASTORAL mesmo após downgrade.

API: GET/POST /pastorais/equipes; PUT /pastorais/equipes/{id}; GET/POST /pastorais/equipes/{id}/membros; GET /pastorais/pessoas?busca=... (mínimo 2 letras, máximo 120). Listas usam pagina a partir de zero. Alteração de equipe exige versao; associação existente também exige a versão atual. Conflito 409 pede recarregar. Permissão USUARIO_ALTERAR também autoriza o seletor mínimo para configurar vínculos sem liberar gerenciamento de pastorais.

Tabelas pastoral_equipe e pastoral_membro têm @TenantId, FKs compostas, índices, versão, RLS e revogação anon/authenticated. calendario_assinatura é global como usuario_tenant, mas todas as consultas autenticadas usam tenant explícito. Mutações relevantes auditadas na mesma transação, sem token em auditoria. Não existe escopo de permissão por pastoral, notificações, arrecadação ou escalas automáticas por grupo nesta versão.

## Onde alterar e verificar

Backend: portal/PortalService, calendario/CalendarioService e Icalendar, pastoral/PastoralService, acesso/VinculoPessoaService, CatalogoPermissao e integracao/FuncionalidadesPlano*. Frontend: features/voluntario, features/pastorais, app.routes e core/layout/menu. Contrato comercial em docs/funcionalidades-plano.md.

Testes HTTP com JWT real verificam pessoa própria, estados publicados, plano explícito, vínculo/assinatura, rotação, revogação, expiração e perda de permissão. Pastorais verificam permissões, isolamento, versões, duplicidade e inativação. Testes do frontend verificam contrato, ausência de ações não implementadas, cancelamento de respostas antigas e token em memória. Execute os comandos do AGENTS.md. Validação automatizada local não substitui revisão visual, staging ou configuração de planos reais.

## Evolução V062 — 02/10/2026

Confirmação/recusa própria e histórico agora implementados conforme [respostas de escala](respostas-escala.md). Referências acima à ausência de confirmação descrevem a entrega V061; candidatura/troca/dependentes permanecem pendentes.
