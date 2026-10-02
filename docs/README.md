# Documentos do servire-api-back

- [Fontes e retomada](desenvolvimento/fontes-e-retomada.md): precedência, estado atual e histórico.

Índice. `AGENTS.md` e `CLAUDE.md` ficam na raiz: as ferramentas de código leem esses nomes ali.

| Arquivo | Para quê |
| --- | --- |
| [../AGENTS.md](../AGENTS.md) | Regras atuais da API |
| [../CLAUDE.md](../CLAUDE.md) | Atalho que importa o AGENTS.md |
| [../README.md](../README.md) | Como rodar e o estado atual |
| [../HISTORICO.md](../HISTORICO.md) | Diário de fases e builds. Não é o contrato de hoje |
| [../plano_mestre_servire_v2_mvp_baixo_custo.md](../plano_mestre_servire_v2_mvp_baixo_custo.md) | Plano de produto. Continua citado pelo AGENTS.md |

- [Limites de integração](integracao-limites.md): payload, nonce e erros do filtro HMAC.

- [financeiro.md](financeiro.md): operação, permissões, cálculos e limites do financeiro paroquial.

- [Cuidados e credenciais](seguranca-cuidados-credenciais.md): permissões, cifra e rotação.
- [Fila de comunicados](fila-comunicados.md): reservas, concorrência e limite da garantia de entrega.

## Entrada rápida

- [estado-projeto.json](estado-projeto.json): componente, comandos e migration local quando houver. Não confirma publicação.
- Histórico explica decisões antigas; contrato e código atuais definem o comportamento vigente.
- [Mapa do backend](desenvolvimento/mapa-backend.md), [armadilhas Java](desenvolvimento/armadilhas-java.md) e [testes](desenvolvimento/testes.md).

- [Listas paginadas](listas-paginadas.md): contratos, ordenação, seleção por página e compatibilidade.

- [Limites de login](login-limites.md): tentativas, saturação e limites por instância.

- [Cotas do plano](cotas-plano.md): comportamento, contratos e limitações.

- [Armazenamento e cotas](armazenamento-cotas.md): bytes, fotos compartilhadas, inventário antigo e limites da medição.

- [Envios mensais e CSV](cotas-envios-importacao.md): unidades, reserva, idempotência e importação de pessoas.

## Quatro entregas de produto — 01/10/2026

- [Funcionalidades por plano](funcionalidades-plano.md).
- [Mural e tarefas](mural-tarefas.md).
- [Consumo de instâncias](consumo-instancias.md).

- [Portal, calendário e pastorais](portal-calendario-pastorais.md): configuração, identidade pessoal, assinatura privada e equipes simples.

- [Respostas de participação](respostas-escala.md): confirmar/recusar, prazo, histórico e consulta da coordenação.

- [Candidaturas a vagas](candidaturas-vagas.md): portal, decisão da coordenação, elegibilidade e concorrência.

- [Trocas de escala com aceite e aprovação](trocas-escala.md).

- [Tarefas, relatórios e indisponibilidade](tarefas-relatorios-disponibilidade.md): responsável, CSV autorizado e portal próprio (V065).

- [Primeiros passos](onboarding.md): checklist compartilhado, pré-requisitos, retomada e revisão versionada (F07/V066).

## Rodada 6–10 — 02/10/2026

- [mural publico leituras ](mural-publico-leituras.md): contrato, limites e homologação.
- [historico consumo ](historico-consumo.md): contrato, limites e homologação.
- [arraste escala ](arraste-escala.md): contrato, limites e homologação.
- [aniversarios ](aniversarios.md): contrato, limites e homologação.
- [site publico ](site-publico.md): contrato, limites e homologação.

## Rodada 11–17 — 02/10/2026

- [liturgia ](liturgia.md).
- [indicadores-participacao ](indicadores-participacao.md).
- [estoque-patrimonio ](estoque-patrimonio.md).
- [seletores-escala ](seletores-escala.md).
- [monitoramento ](monitoramento.md).
