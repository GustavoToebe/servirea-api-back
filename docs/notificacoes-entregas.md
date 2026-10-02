# Centro de entregas e notificações — F05/F13

Avisos de escala e mural enfileirados na fila de comunicados já existente ([fila](fila-comunicados.md)). Sem IA e sem envio fora da fila: o módulo `notificacao/` só monta o texto, confere contato e autorização e registra a entrega. V077: `notificacao_config` (gatilhos) e `notificacao_entrega` (histórico), ambas com RLS sem policy.

## Regras
- Escala: só escala FINALIZADA. Uma mensagem por pessoa escalada, com todas as suas participações (data, hora, celebração, função). Linhas de referência não contam.
- Mural: só aviso PUBLICADO e na versão atual. Público TODOS ou SELECIONADOS, resolvido para contas ativas **com pessoa vinculada explicitamente** (não se infere identidade por e-mail ou nome). Conta sem vínculo não recebe.
- WhatsApp exige voluntário com `autorizaWhatsapp` e telefone; e-mail exige e-mail cadastrado (principal primeiro). Quem não atende conta em `ignorados`; nada é enviado para essas pessoas.
- Idempotência: uma entrega por (origem, referência, versão, canal). Repetir devolve 409. A versão da escala/aviso muda ao reabrir/editar, permitindo novo aviso consciente. Escala e aviso são travados (PESSIMISTIC_WRITE) durante o registro.
- Sem destinatário elegível, o envio manual responde 400 e não registra; o automático registra com total 0 para evitar tentativas repetidas.
- Limite de 2.000 pessoas por notificação (400; no gatilho automático do mural a notificação é pulada e a coordenação decide pelo manual).
- Exige o recurso COMUNICACAO do plano. Cotas mensais, ritmo, tentativas e modo `log` são os da fila.

## Gatilhos automáticos
Desligados por padrão, por origem (ESCALA, MURAL) e canal (EMAIL, WHATSAPP). Escala: dispara dentro da transação de finalizar, depois de gravar a versão nova. Mural: dispara ao criar um aviso publicado ou quando um aviso arquivado volta a publicado; editar um aviso já publicado não notifica sozinho. Plano sem COMUNICACAO ou gatilho desligado = nada acontece e a operação principal segue normalmente.

## API
- GET /notificacoes/configuracoes (NOTIFICACAO); PUT /notificacoes/configuracoes/{origem}/{canal} com `{ativo,versao}` (NOTIFICACAO_CONFIGURAR; 409 se a versão mudou).
- GET /notificacoes/entregas?origem=&pagina=0 (NOTIFICACAO): páginas de 30, título da escala/aviso e contagem de PENDENTE, ENVIADO e FALHA dos destinatários. ENVIADO é aceitação do provedor, não leitura.
- POST /escalas/{id}/notificacoes `{canal}` e POST /mural/avisos/{id}/notificacoes `{canal,versao}` (NOTIFICACAO_ENVIAR).

## Limites
Sem preferências por pessoa além da autorização de WhatsApp, sem horário silencioso, sem lembrete antes da celebração e sem notificação de troca/candidatura. O texto é fixo (não usa layouts). Perfis existentes precisam dos novos códigos; ADMIN já tem o catálogo. Homologar com provedor real e opt-in verificado antes de ligar gatilhos.

## Lembrete de escala (V081)
Origem `ESCALA_LEMBRETE`, gatilho por canal, desligado por padrão. Um job (`LembretesDeEscala`, de hora em hora das 8h às 20h de Brasília, `servire.notificacoes.lembretes-ativo`) avisa quem está escalado em celebrações de escala FINALIZADA que começam nas **próximas 24 horas**, exceto quem recusou a participação, só com contato e, no WhatsApp, autorização. Um aviso por celebração e canal (chave com versão 0); rodar de novo não repete. Linhas de referência, escalas em rascunho e celebrações passadas ficam de fora. A entrega aparece no centro de entregas com o nome e a data da celebração.
