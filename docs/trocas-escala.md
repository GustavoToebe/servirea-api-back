# Trocas de escala com aceite e aprovação

Implementação V064, 02/10/2026. Substituição simples de uma vaga; não faz permuta automática de duas alocações.

## Uso

1. Portal → Trocas de escala: selecionar compromisso futuro e buscar o nome do substituto (mínimo 2 letras, até 30 resultados).
2. O indicado abre a mesma tela e aceita ou recusa o pedido recebido.
3. Coordenação → montagem da escala → Trocas: aprovar ou recusar após o aceite.

A pessoa original permanece alocada até a aprovação. Aceitar não aloca nem confirma presença. Aprovar substitui somente aquela vaga, reinicia resposta de participação e presença para PENDENTE e registra auditoria na mesma transação. Solicitante pode cancelar antes da aprovação. Sem disparo de e-mail ou WhatsApp nesta etapa.

## Segurança e regras

Pessoa solicitante/substituto das ações pessoais vem do vínculo explícito da conta/JWT. Tenant nunca vem do body. Diretório só mostra UUID/nome de outros voluntários ativos da mesma paróquia com conta ativa vinculada; sem contato, cuidados ou ficha. Ações próprias não são ampliadas pelo papel administrativo.

Permissões: PORTAL_VOLUNTARIO para histórico próprio; PORTAL_TROCAR para pedir/aceitar/recusar/cancelar e buscar nomes. VAGA_TROCA_LER para histórico da coordenação; VAGA_TROCA_DECIDIR para decisão. Plano PORTAL_VOLUNTARIO exigido para consultas pessoais/criação/aceite; aprovação/recusa da coordenação também exige ESCALAS. Cancelamento próprio permitido na API após downgrade, com permissão/vínculo; a tela continua protegida pelo plano. Nenhum recurso comercial novo.

Serviço bloqueia paróquia antes da escala, exige versão do pedido/vaga e revalida ciclo, FINALIZADA, sem referência e início futuro (Brasília). Substituto precisa de função habilitada, disponibilidade positiva compatível, ausência de indisponibilidade e de outra alocação FINALIZADA na mesma celebração ou com o mesmo início. Revalida antes do aceite e da aprovação, além dos vínculos ativos dos participantes. Regras compartilhadas com candidaturas em ElegibilidadeEscalaService. Não calcula duração, deslocamento ou conflito com eventos gerais.

Pedido é vinculado à versão da vaga e ao horário/função: resposta de participação, presença, mudança de pessoa ou reabertura pode invalidá-lo. Atualizar e criar outro pedido com versão vigente. Não há retry automático de escrita. Um pedido ativo por vaga, protegido por locks e índice único parcial. Reabrir/cancelar escala expira pedidos aguardando aceite ou aceitos. Histórico conserva UUIDs e dados mínimos após exclusão da vaga/escala/pessoa; nomes removidos não são armazenados no histórico. Páginas de 30, nomes/vagas consultados em lote.

## Contrato

| Método | Rota | Corpo / consulta |
|---|---|---|
| GET | /portal/trocas/substitutos | busca (2–80 caracteres, curingas tratados literalmente) |
| GET | /portal/trocas | pagina |
| POST | /portal/vagas/{id}/trocas | substitutoId, versao da vaga |
| PUT | /portal/trocas/{id}/aceite | aprovar, versao do pedido |
| PUT | /portal/trocas/{id}/cancelamento | versao do pedido |
| GET | /escalas/{id}/trocas | pagina |
| PUT | /escalas/trocas/{id}/decisao | aprovar, versao do pedido |

Situações: AGUARDANDO_ACEITE → ACEITA → APROVADA ou RECUSADA; antes da aprovação, CANCELADA/EXPIRADA. Histórico pessoal inclui somente pedidos enviados/recebidos pela pessoa atual; coordenação vê somente a escala do tenant autenticado.

## Implantação e homologação

Aplicar V064 pelo Flyway no ambiente autorizado; configurar PORTAL_VOLUNTARIO/ESCALAS no plano, permissões nos perfis e vínculos pessoais dos participantes. Sem alteração automática de dados/perfis/planos reais. Conferir visualmente com três contas (solicitante/substituto/coordenação): solicitar, aceitar, aprovar, recusar, cancelar, conflito de horário e reabrir escala. Testes automatizados locais não substituem essa homologação em staging. Responsáveis/dependentes, permuta bilateral, notificações e prazos configuráveis seguem pendentes.

## Validação local em 02/10/2026

Maven clean verify: 501 testes aprovados, zero falhas/erros/ignorados. Frontend: 393 aprovados, 1 ignorado preexistente; build de produção aprovado (avisos preexistentes de dependências CommonJS). Inclui testes HTTP com JWT, fluxo completo, aprovação concorrente, preservação da alocação, recusas/cancelamento, vínculo alterado, disponibilidade, horário, isolamento, permissões e versões. Formulário e API frontend verificados. Schema regenerado de 64 migrations em PostgreSQL 17 descartável, container removido ao concluir. Checagem documental e git diff --check aprovados. Sem homologação visual em staging, merge, implantação ou IA.
