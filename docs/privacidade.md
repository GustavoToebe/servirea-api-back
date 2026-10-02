# Privacidade: consentimentos, exportação e retenção — F09

Módulo `privacidade/`, V078 (`consentimento_historico`, `retencao_politica`, `retencao_execucao`, todas com RLS sem policy). Sem IA, sem agendador e sem envio de dados para fora do sistema.

## Histórico de consentimentos
Cada mudança de autorização grava uma linha imutável (tipo, concedido, fonte, quem registrou, quando). Tipos: WHATSAPP (autorização no perfil de voluntário), ANIVERSARIO_EMAIL e ANIVERSARIO_WHATSAPP. Registra: criação com autorização ligada, edição que muda o valor (editar sem mudar não grava), aprovação de inscrição pública com autorização e autorizar/revogar felicitação de aniversário. O estado vigente continua no cadastro e nas tabelas de origem; o histórico não decide envio. Cadastros anteriores a V078 não têm histórico retroativo. Apagar a pessoa apaga o histórico dela (FK em cascata).

GET /pessoas/{id}/consentimentos?pagina=0 (PRIVACIDADE), páginas de 30, mais recente primeiro.

## Exportação autorizada
GET /pessoas/{id}/exportacao (PRIVACIDADE_EXPORTAR) devolve JSON como anexo, com `Cache-Control: no-store`: identidade, endereço, contatos, relações (só o nome do outro lado), perfil de voluntário, até 500 participações, até 500 consentimentos e até 500 comunicações (canal, origem, assunto, situação e data). **Nunca** inclui o corpo das mensagens nem o endereço de destino. Cuidados e acolhimento só entram com PESSOA_CUIDADOS_LER. Cada exportação gera auditoria EXPORTAR_DADOS. Não há exportação em lote nem para terceiros.

## Retenção
GET/PUT /privacidade/retencao e POST /privacidade/retencao/execucao. Prazo opcional de 30 a 3650 dias (nulo = não aplicar), com versão (409 se mudou). PRIVACIDADE lê; PRIVACIDADE_RETENCAO altera e executa. A execução é manual: destinatários ENVIADO/FALHA de comunicados CONCLUÍDOS e mais antigos que o prazo têm nome, contato e texto trocados por `[removido pela política de retenção]`, assunto e erro limpos e o vínculo com a pessoa removido. Não desfaz, é idempotente (já anonimizados não contam) e registra execução e auditoria. A resposta de GET mostra quantos seriam afetados e as 10 últimas execuções.

## Limites
Retenção cobre só comunicados. Logs de auditoria, fotos, cadastros inativos e dados de eventos continuam sem prazo automático. Sem solicitação pelo titular, sem fluxo de eliminação da pessoa e sem prova jurídica de consentimento (a fonte é texto informado). Perfis existentes precisam dos códigos novos; ADMIN já tem. Definir prazo e base legal com a paróquia antes de executar em produção.
