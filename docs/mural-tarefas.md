# Mural e tarefas simples

Implementação V060, ampliada em 02/10/2026 pela V065. Não confirma implantação.

## Mural

Menu Mural, rota /mural. Aviso interno com título (160 caracteres), descrição em texto simples (4.000), status PUBLICADO/ARQUIVADO e data de referência opcional. A data não publica, expira ou arquiva automaticamente. Filtre PUBLICADO para ver os avisos atuais. Detalhe conserva o histórico arquivado. Texto não é renderizado como HTML.

## Tarefas e solicitações

Menu Tarefas, rota /tarefas. Título, descrição, prazo opcional, equipe responsável em texto (120 caracteres) e status ABERTA/EM_ANDAMENTO/CONCLUIDA/CANCELADA. São registros compartilhados com os usuários que têm acesso ao módulo na paróquia. Equipe é um rótulo, não atribuição a uma conta, grupo ou permissão. Responsável vinculado à conta é um campo separado, opcional (V065); diretório mínimo e filtros próprios em [contrato atual](tarefas-relatorios-disponibilidade.md). Não há fluxo de aprovação ou restrição por equipe.

## Segurança e contratos

Recursos comerciais MURAL e TAREFAS devem constar explicitamente em funcionalidades dos direitos enviados pela Central. Sem código, leitura permanece conforme perfil, mutações dão 403 FUNCIONALIDADE_NAO_CONTRATADA. Não há liberação automática para instalações antigas. Cadastre os recursos e inclua-os nos planos desejados; não foram alterados planos reais. Recarregue a página após atualizar direitos.

Permissões: MURAL/MURAL_CRIAR/MURAL_ALTERAR e TAREFA/TAREFA_CRIAR/TAREFA_ALTERAR. Criar/alterar são ações diferentes. Acesso total inclui os novos códigos; perfis limitados precisam ser configurados. Tenant vem do JWT. @TenantId e RLS sem policies/revogação anon/authenticated protegem as duas tabelas. Leitura e escrita em outra paróquia respondem 404, sem revelar o registro. Mudanças registram auditoria na mesma transação.

API: GET /mural/avisos e /tarefas com busca (título literal), status, pagina (zero) e tamanho (1–100; padrão 30). Retorno {itens,total,pagina,tamanho}. Ordenação criadoEm/id descendentes. GET /{rota}/{id}, POST /{rota}, PUT /{rota}/{id}. Corpo título, descrição, status, prazo ISO ou null; tarefas também equipe e responsavelUsuarioId (UUID ou null). PUT exige versao da última resposta; conflito 409 exige atualizar antes de salvar. Não existem DELETE ou exclusão de histórico.

## Limites desta entrega

Sem notificações, confirmação de leitura, anexos, publicação pública, lembretes, recorrência de tarefas, comentários ou kanban. Atribuição a usuário está disponível pela V065. Mural atende a primeira parte de F13; confirmação de leitura/notificações permanecem no backlog. Tarefas atendem o núcleo simples de F21; fluxos especializados permanecem no backlog. Sem IA.
