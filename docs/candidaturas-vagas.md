# Candidatura a vagas com aprovação da coordenação

Implementação local de 02/10/2026, Servirea V063. Sem IA e sem implantação em produção.

## Fluxo de uso

Meus compromissos → Vagas e candidaturas (/portal/vagas). Selecione período de até 93 dias e consulte vagas livres futuras em escalas FINALIZADAS, excluindo referências. Lista de 30, ordenada por data/hora/id. Mostra celebração, início, função, versão e elegibilidade da própria pessoa; não expõe participantes/candidatos de terceiros. Cadastro sem vínculo pessoal recebe orientação de configuração.

Candidatar-se cria pedido PENDENTE e não reserva a vaga. Minhas candidaturas mostra somente pedidos da pessoa atualmente vinculada à conta, incluindo encerrados, em páginas de 30. Desistir encerra pedido pendente. Uma candidatura desistida pode ser renovada se a vaga continua elegível no mesmo ciclo; mantém ID e auditoria. Pedido recusado não é reapresentado naquele ciclo. Repetir pedido pendente não cria duplicidade.

Na montagem da escala, Candidaturas abre /escalas/{id}/candidaturas. Coordenação vê nome da pessoa e resumo da celebração, sem contatos ou cuidados, e pode Aprovar/Recusar. Aprovar ocupa a vaga na escala FINALIZADA e encerra outros pedidos pendentes para ela. Recusar preserva a vaga livre. Aprovação deixa a resposta de participação PENDENTE e presença PENDENTE: a pessoa vê a nova alocação no portal e pode confirmá-la pelo fluxo V062. Não há WhatsApp/e-mail ou confirmação automática.

## Elegibilidade e prazo

Pessoa deve ter perfil de voluntário ativo, função explicitamente habilitada, disponibilidade compatível e nenhuma indisponibilidade do dia/período. Sem disponibilidade positiva cadastrada, ela é opt-in (como o picker existente); indisponibilidade explícita sempre impede. Períodos: antes de 12h manhã, 12h–17h59 tarde, a partir de 18h noite. Não há matriz adicional tipo × função.

Não pode estar alocada na mesma celebração ou em outra celebração FINALIZADA com data e horário de início iguais. Não calcula deslocamento, duração/intervalo ou choque com eventos gerais; não é um motor completo de agenda. A regra é revalidada na candidatura e na aprovação, junto com vínculo pessoal ativo, versão da vaga, vaga livre e início futuro em Brasília. Mudança de disponibilidade após o pedido pode impedir aprovação.

Prazo é o início da celebração, conferido no servidor. Reabrir ou cancelar a escala expira pedidos pendentes. Reabertura inicia novo ciclo da vaga via versão, inclusive vaga vazia; pedidos anteriores não aprovam naquele ciclo. Editar a escala já recria vagas, e IDs anteriores conservam apenas histórico. Pedido pendente com vaga removida/ocupada, versão diferente ou prazo vencido aparece como EXPIRADA na consulta; essa avaliação não grava em GET. Aprovação concorrente de outro pedido expira os restantes de forma persistente. Candidatura aprovada continua como registro histórico mesmo se a coordenação alterar a alocação depois.

## Permissões e plano

Leitura pessoal exige PORTAL_VOLUNTARIO no perfil e no plano. Ação PORTAL_CANDIDATAR permite criar/renovar/desistir da própria candidatura. Coordenação usa VAGA_CANDIDATURA_LER para consultar e VAGA_CANDIDATURA_DECIDIR para decidir; configure ambas para a tela de decisão. Decisão exige ESCALAS e PORTAL_VOLUNTARIO nos direitos comerciais. Nenhuma funcionalidade comercial nova foi criada.

Desistência no backend continua possível após downgrade, com permissão e vínculo pessoal; a página pessoal exige recurso para consultar e não constitui um portal independente de cancelamento após downgrade. Sem recursos/permissões, 403. Tenant/pessoa/usuário nunca vêm do body. Conta de suporte não pode candidatar-se como pessoa. IDs de outra paróquia/pessoa respondem 404. Versão/situação/elegibilidade alteradas retornam 409; recarregar sem retry automático.

Configure permissões dos perfis e direitos antes de implantar. Acesso total acompanha o catálogo; perfis limitados precisam de configuração e nova sessão. Nenhum plano, perfil, vínculo ou contrato real foi alterado.

## API e concorrência

GET /portal/vagas-abertas?de=AAAA-MM-DD&ate=AAAA-MM-DD&pagina=0; GET /portal/candidaturas?pagina=0; POST /portal/vagas/{id}/candidaturas com {versao: versão da vaga}; PUT /portal/candidaturas/{id}/desistencia com {versao: versão do pedido}.

GET /escalas/{id}/candidaturas?pagina=0; PUT /escalas/candidaturas/{id}/decisao com {aprovar: boolean, versao: versão do pedido}. Listas retornam {itens,total,pagina,tamanho}; tamanho fixo 30. Pedido tem id/vagaId, snapshot da celebração/início/função, situação, versão, datas, pessoaNome (somente coordenação) e vigente. Situações PENDENTE/APROVADA/RECUSADA/DESISTIDA/EXPIRADA. Não há filtro de situação nesta primeira tela.

Mutações travam paróquia antes da escala/vaga. Decisões são serializadas por paróquia, protegendo duas aprovações da mesma vaga ou do mesmo voluntário em horários iguais. @Version protege pedido/vaga; unicidade por tenant/vaga/pessoa/versão impede duplicação. Vaga é reconsultada sob lock da escala; identidade pessoal é conferida antes de aprovar. Aprovação, alocação, encerramento de concorrentes e auditoria ocorrem na mesma transação; conflito desfaz tudo.

Consulta de vagas usa projeções/entidades com joins das referências e quatro consultas de elegibilidade por pessoa/período, reaproveitadas na página. Consulta de pedidos faz batch de vagas e nomes de no máximo 30; não carrega fichas completas nem todos os voluntários. JPQL/JPA respeitam @TenantId, sem SQL nativo em domínio.

Tabela escala_candidatura tem @TenantId, índices, @Version, RLS sem policies e revogação anon/authenticated. Guarda UUIDs históricos e snapshot da celebração, sem nome/contato/cuidado persistido. IDs de vaga/pessoa não têm cascata de exclusão; histórico sobrevive à edição/exclusão do domínio. Auditoria registra mudanças sem motivos livres. Política de retenção/privacidade geral permanece no backlog.

## Limites e verificação

Testes HTTP com JWT real: duplicidade, aprovação/recusa/desistência, renovação, duas aprovações simultâneas, conflito de horário, elegibilidade/vínculo alterado, prazo, reabertura e novo ciclo, histórico após exclusão, isolamento de pessoa/paróquia, plano/permissão, DTO e paginação. Frontend verifica bodies mínimos, versões, permissões, consulta pessoal versus coordenação, resultados antigos cancelados, conflito sem retry e confirmação tardia após sair da tela.

Não foi feita homologação visual/manual em ambiente implantado. Próximo módulo: troca de escala com aceite do substituto e aprovação da coordenação. Pendentes: notificações, prazo antecipado configurável, responsáveis/dependentes, filtro/relatório histórico de candidaturas e agenda com intervalos/deslocamento. Alocação manual legada continua com suas regras; a verificação de conflito aqui protege este fluxo de candidatura e não reescreve todo o editor de escalas.

## Evolução V064

Elegibilidade compartilhada com [trocas de escala](trocas-escala.md): função, disponibilidade, bloqueios e alocações simultâneas seguem as mesmas regras.
