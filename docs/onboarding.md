# Primeiros passos — F07

Implementação de 02/10/2026, Servirea V066. Este documento descreve código local; não confirma implantação.

## Comportamento

Menu Primeiros passos, rota `/primeiros-passos`, também acessível pelo painel inicial. O checklist é compartilhado por paróquia, salvo no servidor e pode ser retomado em outra sessão/conta autorizada. Consultar não grava nem conclui etapas automaticamente. Abrir o módulo, configurar, voltar e registrar a revisão. Reabrir preserva todos os dados do módulo.

| Etapa | Pré-requisito verificado pelo servidor | Módulo |
|---|---|---|
| Paróquia | Nome, cidade e UF preenchidos | PAROQUIA |
| Equipe | Pelo menos duas contas com vínculo ativo na paróquia | USUARIO |
| Pessoas | Pelo menos uma pessoa cadastrada | PESSOA |
| Voluntários | Pelo menos um voluntário ativo | PESSOA |
| Primeira escala | Vaga ocupada em evento sem referência de escala FINALIZADA | ESCALA + recurso ESCALAS |

Equipe pode ser dispensada com “Trabalho sozinho” e reaberta depois. Vínculo ativo não confirma que o convidado já entrou. Pessoas podem ser cadastradas ou importadas pelo fluxo existente e suas cotas/permissões; o checklist não importa arquivos. Funções, disponibilidade e contatos são conferências manuais orientadas pela tela, não validações completas automáticas.

Estados: PENDENTE, PRONTA, CONCLUIDA, DISPENSADA, REVISAR, SEM_PERMISSAO e NAO_CONTRATADA. Revisão registrada cujo pré-requisito deixou de existir aparece como REVISAR sem apagar histórico. Percentual considera somente etapas acessíveis e contratadas. A próxima etapa é a primeira dessas ainda não revisada. Usuários com perfis diferentes podem ver percentuais diferentes; isso não é uma medição global de conversão comercial.

## Segurança e concorrência

ONBOARDING permite consultar; ONBOARDING_GERENCIAR permite registrar revisão, junto à permissão de leitura do módulo correspondente. Os perfis personalizados precisam dessas permissões; administradores seguem o catálogo existente. O checklist não concede acesso a módulos nem recursos do plano. Sem permissão do módulo não consulta fichas, requisitos ou contagens desse módulo. Mostra só orientação estática e estado SEM_PERMISSAO, sem link ou ações.

Tenant vem da sessão JWT. Consultas de domínio usam @TenantId; UsuarioTenant e Tenant globais usam explicitamente a paróquia da sessão. Consultas de existência retornam no máximo um registro (dois vínculos para equipe), sem contatos/documentos. Escritas bloqueiam a paróquia antes de ler/gravar o progresso. Versão 0 significa ausência; primeira revisão cria versão 1. Toda escrita exige a versão lida e incrementa; duas telas antigas geram HTTP 409 em vez de sobrescrever. Registro e auditoria compartilham a transação. Tabela com RLS e revogação anon/authenticated; sem acesso direto Supabase.

## Contrato

GET `/onboarding`: versão, cinco etapas, concluídas/total/percentual, próxima etapa e momentos da primeira/última revisão. Não cria progresso.

PUT `/onboarding/etapas/{PAROQUIA|CONVITE|PESSOAS|VOLUNTARIOS|ESCALA}`: `{ "acao": "CONCLUIR|REABRIR|PULAR", "versao": 0 }`. PULAR só vale para CONVITE. CONCLUIR valida pré-requisito e recurso ESCALAS. Retorna o checklist atualizado; 400 para configuração incompleta/ação inválida, 403 para permissão/recurso ausente e 409 para versão antiga. Não aceitar tenant no corpo.

Frontend confirma a ação em diálogo, impede escrita dupla enquanto aguarda confirmação/resposta, preserva os dados e não repete escrita após erro; usuário atualiza antes de tentar novamente. Cancelar ou sair da página durante confirmação não grava.

## Homologação manual

1. Aplicar V066 e atualizar API/frontend em staging pelo fluxo autorizado.
2. Administrador consulta sem criar registro; configura paróquia, conta, pessoa, voluntário e escala; registra etapas e retoma em outra conta da mesma paróquia.
3. Conta de outra paróquia tem progresso independente. Perfil somente leitor não altera; perfil sem permissão de módulo não recebe seus dados/ações.
4. Dispensar equipe, reabrir e concluir. Plano sem ESCALAS não oferece ação/link e não conta a etapa no total.
5. Duas telas com a mesma versão: uma salva; outra recebe 409. Atualizar permite retomar sem apagar a revisão da equipe.
6. Inativar voluntário ou remover pré-requisito após revisão: etapa pede nova conferência. Testar teclado, tela pequena e falha de rede.

O mínimo F07 entrega checklist, progresso e retomada. Taxa de pilotos concluindo sem assistência depende de usuários reais e definição de métricas; não existe painel analítico/telemetria de conversão nesta entrega. Sem IA, novos envios, novos recursos comerciais ou alterações de planos reais.

## Evidência local de 02/10/2026

Maven clean verify: 533 testes aprovados, sem falhas/erros/ignorados. Frontend: 429 aprovados e um ignorado preexistente; build de produção aprovado. Acrescentados 11 testes HTTP e 16 frontend. As 66 migrations foram aplicadas em PostgreSQL 17 descartável; schema regenerado e container removido. Checagem documental aprovada. Homologação visual em staging ainda pendente.
