# Banco do Servire

Postgres da paróquia. A Central tem o banco dela (clientes, contratações, cobrança). Este arquivo diz o que o código do Servire usa de verdade. Atualizar quando entrar migration nova.

Flyway: `src/main/resources/db/migration`, V001–V075. Migration já aplicada não se edita.

## O que entrou na V066

Tabela onboarding_progresso com estado compartilhado por tenant, revisão por etapa, versão manual, timestamps, unicidade, RLS e revogação. Contrato em [primeiros passos](docs/onboarding.md).

## O que entrou na V065

Responsável de tarefa com FK do vínculo local, versão mensal de indisponibilidades e índices de tarefas/relatório. Tabela mensal com RLS/revogação. Contrato em [tarefas, relatórios e disponibilidade](docs/tarefas-relatorios-disponibilidade.md).

## O que entrou na V064

escala_troca guarda pedido de substituição versionado, participantes, ciclo e snapshot mínimo. Índice único parcial impede dois pedidos ativos por vaga; índices de listas próprias e coordenação; RLS/revogação. Referências históricas sem cascata. Contrato em docs/trocas-escala.md.

## O que entrou na V063

escala_candidatura armazena pedidos versionados com ciclo da vaga, snapshots mínimos da celebração, índices, unicidade tenant/vaga/pessoa/ciclo, RLS e revogação. Referências históricas sem cascata de domínio. Contrato em docs/candidaturas-vagas.md.

## O que entrou na V062

escala_vagas ganha resposta PENDENTE/CONFIRMADA/RECUSADA, resposta_em e resposta_versao (@Version). escala_resposta_historico guarda decisões pessoais com @TenantId, índice, RLS/revogação e UUIDs históricos sem cascata do domínio. Uso em docs/respostas-escala.md.

## O que entrou na V060–V061

V060: mural_aviso e tarefa, registros simples, versionados e paginados. V061: associação explícita usuario_tenant.pessoa_id (FK composta e unicidade por pessoa/paróquia), pastoral_equipe e pastoral_membro tenant-aware, calendario_assinatura global com hash secreto/expiração e assinatura única por usuário/paróquia. Todas as novas tabelas têm RLS sem policies e revogação anon/authenticated. Contratos em docs/mural-tarefas.md e docs/portal-calendario-pastorais.md.

## O que entrou na V058–V059

Competência durável de cota nos destinatários, índice mensal e backfill de envios confirmados. Registro de lotes CSV concluídos sem arquivo/dados pessoais, chave idempotente por paróquia, quantidade e competência. Tabela com RLS e revogação de acesso direto. Uso em docs/cotas-envios-importacao.md.

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

Globais (sem `@TenantId`): `usuario`, `tenant`, `diocese`, `perfil`, `perfil_permissao`, `usuario_tenant`, `refresh_token`, `password_reset_token`, `tenant_email`, `tenant_telefone`, `direitos_locais`, `integracao_operacao`, `integracao_nonce`, `suporte_codigo`, `tenant_sequencial`, `calendario_assinatura`.

`tenant_sequencial` não tem entidade. O gatilho da V038 grava o número curto.

Da paróquia (`@TenantId`): `pessoa`, `pessoa_email`, `pessoa_telefone`, `pessoa_relacao`, `voluntarios`, `disponibilidade_voluntario`, `indisponibilidade_voluntario`, `resposta_indisponibilidade`, `inscricoes`, `inscricao_email`, `inscricao_telefone`, `inscricao_responsaveis`, `inscricao_responsavel_email`, `inscricao_responsavel_telefone`, `escalas`, `escala_eventos`, `escala_vagas`, `layout_escala`, `layout_envio`, `paroquia_whatsapp`, `comunicado`, `comunicado_destinatario`, `comunicado_anexo`, `evento`, `evento_foto`, `evento_inscricao`, `audit_log`, `financeiro_conta`, `financeiro_categoria`, `financeiro_movimento`, `mural_aviso`, `tarefa`, `pastoral_equipe`, `pastoral_membro`, `escala_resposta_historico`, `escala_candidatura`.

View `vw_voluntario_compromissos` (V010) existe. A lista de compromissos é montada via JPA, não via essa view.

## O que não apagar

- Contato e endereço em `tenant` (V027): a ficha da paróquia usa.
- `tenant.vigencia_ate`: a Central e o Kill Switch dependem do status do tenant. `tipo_email` existiu na V028 e saiu na V030 (o e-mail da paróquia foi para `tenant_email`).
- `tenant.ultimo_pagamento_em`: a entidade `Tenant` ainda mapeia a coluna.
- `flyway_schema_history`: controle do Flyway.

O banco da Central está no `SCHEMA.md` do `central-api-back`. Os nomes `plano` e `cobranca` existem lá com outro desenho: plano de um produto, cobrança de uma contratação.

## Esquema completo
`schema.sql` (nesta pasta) é o esquema inteiro, com colunas, chaves, índices, gatilhos e enums, gerado das migrations V001–V060 num Postgres limpo. Serve para saber o estado do banco sem abrir o Supabase. **Migration nova: rodar `scripts/gerar-schema.ps1` e commitar o `schema.sql` junto.** Nunca editar o arquivo à mão.
V054 introduz o financeiro **paroquial**: contas com saldo inicial, categorias e lançamentos versionados com baixa/estorno. Não recria planos e cobranças comerciais removidos na V051; esses pertencem à Central.

## V055 e V056

V055 aumenta token WhatsApp para text: conteúdo cifrado AES-256-GCM com versão de chave e vínculo à paróquia. V056 registra próxima tentativa e posse temporária no destinatário e cria fila_envio_janela, coordenação global de ritmo EMAIL e por paróquia no WHATSAPP. Tabela de coordenação sem dados pessoais, com RLS e sem acesso anon/authenticated.

## Tamanho de fotos (V057)

voluntarios, inscricoes e evento_foto recebem foto_tamanho_bytes bigint positivo ou NULL (legado desconhecido). Fotos compartilhadas contam uma vez; anexos retidos já têm tamanho. Ver docs/armazenamento-cotas.md.

## Mural e tarefas (V060)

mural_aviso e tarefa são entidades @TenantId, com título/descrição em texto simples, status, prazo/data de referência, timestamps e versão otimista. Tarefa tem equipe como rótulo opcional. Índices por paróquia/status/criação/id; RLS sem policies e acesso Data API revogado. Sem exclusão física ou relações globais de responsável. V058 registra competência da fila e V059 importacao_pessoa, também tenant-aware. Ver docs/mural-tarefas.md.

## Rodada 6–10 — 02/10/2026

V067 público/leitura do mural; V068 resumo diário de consumo; V069 aniversários autorizados/configuração/execução; V070 rascunho/publicação da paróquia. Manuais e limites no [índice](docs/README.md). Schema exportado de PostgreSQL 17 descartável após aplicação integral das migrations.

## V071–V074

Referências/roteiros manuais; itens e movimentos de estoque/patrimônio com saldo/versionamento, FK de responsável local, chave de repetição e histórico; índices de seletores por tenant/prefixo/ativo/ordenação. Ver docs/liturgia.md, docs/estoque-patrimonio.md e docs/seletores-escala.md. Novas tabelas com RLS e revogação.

## V075 — MFA global do usuário

Segredos TOTP ativo/pendente cifrados, expiração, último passo aceito e versão de credenciais em usuario. usuario_mfa_recuperacao armazena hashes de códigos de uso único, RLS sem policies e revogação da Data API. Conta global; não vincular fator a tenant.
