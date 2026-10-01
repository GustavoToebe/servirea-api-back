# Fila de comunicados

## Processamento e concorrência

Destinatário registra próxima tentativa, UUID de posse e validade de 120 segundos. Worker reserva em transação curta, faz HTTP fora dela e conclui em nova transação. Conclusão confere posse e prazo; resultado antigo não altera outra tentativa. Ordem de locks: janela de envio, comunicado, destinatário.

Um worker de e-mail e três de WhatsApp, cada canal com fila local limitada. Busca até 100 paróquias por rodada com paginação e rotação; não carrega todas em cada ciclo de produção. A janela EMAIL é global; WhatsApp tem janela por paróquia. Ritmo fica no banco (sem sleeps entre mensagens), inclusive com várias réplicas. Limitação de workers é por réplica; a janela global controla o canal compartilhado.

Falhas repetem após 1 e 5 minutos por padrão, até três tentativas. Crash libera a mensagem pela expiração da reserva. Bloqueio comercial é conferido antes do processamento. WhatsApp com pessoa associada confere autorização atual antes de reservar. Anexos são apagados somente quando todos os destinatários chegam a estado final. ENVIADO indica aceitação do provedor, sem comprovar leitura ou entrega final.

HTTP tem connect timeout de 5 segundos e read timeout de 20 segundos. Resend recebe Idempotency-Key estável `comunicado/<destinatario-id>`, reutilizada na recuperação da fila. O provedor guarda a chave por 24 horas, conforme [documentação Resend](https://resend.com/docs/dashboard/emails/idempotency-keys). Fora dessa janela, ou num provedor WhatsApp sem idempotência, a garantia continua sendo pelo menos uma vez: uma falha entre envio e conclusão pode repetir a mensagem.

Não registra destino, token nem corpo de resposta do provedor nas mensagens de falha. Relatos operacionais usam UUID da paróquia e classe do erro. Testes cobrem concorrência, expiração, conclusão obsoleta e HTTP fora de transação. Ainda é necessário ensaio de carga representativo para dimensionar workers, pool de banco e ritmo do provedor.
