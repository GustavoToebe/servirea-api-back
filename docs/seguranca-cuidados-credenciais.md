# Cuidados pessoais e credenciais

## Acesso a cuidados

PESSOA_CUIDADOS_LER controla a leitura de condições, nível TEA, descrição de condição, cuidados e data de consentimento nas respostas de pessoas e inscrições. PESSOA_CUIDADOS_ALTERAR exige também leitura. Alterar implica ler no cadastro de perfis. Coordenador legado não recebe essas permissões automaticamente; administrador/acesso total continua incluindo todas.

Editar dados gerais sem cuidados mantém os cuidados existentes. Tentar escrever cuidados sem permissão responde 403. Respostas de inscrição pública omitem esses dados; a coleta pública mantém a exigência de consentimento já existente. O formulário autorizado carrega e salva os cuidados; sem permissão não os envia.

## WhatsApp

Definir CREDENCIAIS_CHAVES com identificador e chave Base64 de 32 bytes: `v1:<base64>`. Gerar fora do repositório com um gerador criptográfico. CREDENCIAIS_CHAVE_ATIVA escolhe o identificador ativo. Produção exige ambas. Não colocar o segredo em documentação, logs ou controle de versão.

Envelope AES-256-GCM com nonce aleatório de 12 bytes, tag autenticada e AAD vinculada ao UUID da paróquia. Copiar ciphertext para outra paróquia ou modificar o conteúdo falha. Endpoints não retornam token; envio abre a credencial apenas em memória.

Na inicialização, RotacaoCredenciais percorre lotes de 100 e trava cada linha: tokens legados são cifrados e versões antigas recifradas. Sem chave com credenciais existentes, inicialização falha. A fila aguarda ApplicationReadyEvent.

Para rotacionar, adicionar a nova chave ao conjunto, manter a anterior, selecionar a nova chave ativa e reiniciar. Conferir subida e testes operacionais antes de retirar a chave anterior. Backups antigos continuam dependendo das chaves antigas: armazená-las separadamente, com controle de acesso, durante a retenção desses backups. Perder as chaves impede recuperar os tokens.

Em instalações com várias réplicas, preparar o conjunto de chaves em todas antes de ativar uma nova chave, para que todas consigam ler envelopes novos. Backups anteriores ainda podem conter plaintext legado; cifrar o banco atual não reescreve backups existentes.
