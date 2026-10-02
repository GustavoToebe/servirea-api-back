# Documentos do servire-api-back

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
