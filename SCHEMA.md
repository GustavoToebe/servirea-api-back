# Banco do Servire

Postgres da paróquia. A Central tem o banco dela (clientes, contratações, cobrança). Este arquivo diz o que o código do Servire usa de verdade. Atualizar quando entrar migration nova.

Flyway: `src/main/resources/db/migration`, V001–V056. Migration já aplicada não se edita.

## O que entrou na V054

Financeiro paroquial simples: contas, categorias e movimentos. Chaves compostas por tenant, versão e situação do movimento, índices de vencimento/baixa, RLS e revogação do acesso direto Supabase. Uso e cálculos em `docs/financeiro.md`.

## O que entrou na V053

Layouts e e-mail nos eventos (01/10/2026): o tipo de layout `EVENTO` (WhatsApp e e-mail, com um layout padrão de cada por paróquia), em `evento` os interruptores `whatsapp_habilitado`/`email_habilitado` e o layout escolhido para "Ao inscrever" e para "Lembrete" em cada canal (excluir o layout só limpa a escolha), `lembrete_dias` como lista ("1,3,7"; vazio = sem lembrete) e, em `evento_inscricao`, os destinatários da confirmação e do lembrete por e-mail.

## O que entrou na V052

Eventos (01/10/2026): `evento` (data e hora no horário de Brasília, local, mapa, vagas, responsável, dias do lembrete, textos das mensagens e situação RASCUNHO/PUBLICADO/CANCELADO), `evento_foto` (caminho no Storage, uma é a capa) e `evento_inscricao` (pessoa já cadastrada, autorização de WhatsApp no momento da inscrição e os ids dos destinatários da fila dos comunicados para a confirmação e o lembrete).

## O que saiu na V051

Financeiro e painel que moravam neste projeto antes da Central. Sem entidade e sem SQL no código:

| Tabela ou coluna | Origem |
| --- | --- |
| `plano`, `preco_plano`, `assinatura`, `cobranca` | V029 |
| `backoffice_log` | V027 |
| `usuario.operador_saas` | V027 |

Enums apagados junto: `plano_periodicidade`, `forma_pagamento`, `assinatura_status`, `cobranca_status`.

`tenant.ultimo_pagamento_em` ficou. `Tenant` ainda mapeia a coluna.

`responsaveis` (V004) já tinha sido apagada na V030. O vínculo hoje é `pessoa_relacao`.

## Tabelas que o código usa

Globais (sem `@TenantId`): `usuario`, `tenant`, `diocese`, `perfil`, `perfil_permissao`, `usuario_tenant`, `refresh_token`, `password_reset_token`, `tenant_email`, `tenant_telefone`, `direitos_locais`, `integracao_operacao`, `integracao_nonce`, `suporte_codigo`, `tenant_sequencial`.

`tenant_sequencial` não tem entidade. O gatilho da V038 grava o número curto.

Da paróquia (`@TenantId`): `pessoa`, `pessoa_email`, `pessoa_telefone`, `pessoa_relacao`, `voluntarios`, `disponibilidade_voluntario`, `indisponibilidade_voluntario`, `resposta_indisponibilidade`, `inscricoes`, `inscricao_email`, `inscricao_telefone`, `inscricao_responsaveis`, `inscricao_responsavel_email`, `inscricao_responsavel_telefone`, `escalas`, `escala_eventos`, `escala_vagas`, `layout_escala`, `layout_envio`, `paroquia_whatsapp`, `comunicado`, `comunicado_destinatario`, `comunicado_anexo`, `evento`, `evento_foto`, `evento_inscricao`, `audit_log`, `financeiro_conta`, `financeiro_categoria`, `financeiro_movimento`.

View `vw_voluntario_compromissos` (V010) existe. A lista de compromissos é montada via JPA, não via essa view.

## O que não apagar

- Contato e endereço em `tenant` (V027): a ficha da paróquia usa.
- `tenant.vigencia_ate`: a Central e o Kill Switch dependem do status do tenant. `tipo_email` existiu na V028 e saiu na V030 (o e-mail da paróquia foi para `tenant_email`).
- `tenant.ultimo_pagamento_em`: a entidade `Tenant` ainda mapeia a coluna.
- `flyway_schema_history`: controle do Flyway.

O banco da Central está no `SCHEMA.md` do `central-api-back`. Os nomes `plano` e `cobranca` existem lá com outro desenho: plano de um produto, cobrança de uma contratação.

## Esquema completo
`schema.sql` (nesta pasta) é o esquema inteiro, com colunas, chaves, índices, gatilhos e enums, gerado das migrations V001–V056 num Postgres limpo. Serve para saber o estado do banco sem abrir o Supabase. **Migration nova: rodar `scripts/gerar-schema.ps1` e commitar o `schema.sql` junto.** Nunca editar o arquivo à mão.
V054 introduz o financeiro **paroquial**: contas com saldo inicial, categorias e lançamentos versionados com baixa/estorno. Não recria planos e cobranças comerciais removidos na V051; esses pertencem à Central.

## V055 e V056

V055 aumenta token WhatsApp para text: conteúdo cifrado AES-256-GCM com versão de chave e vínculo à paróquia. V056 registra próxima tentativa e posse temporária no destinatário e cria fila_envio_janela, coordenação global de ritmo EMAIL e por paróquia no WHATSAPP. Tabela de coordenação sem dados pessoais, com RLS e sem acesso anon/authenticated.
