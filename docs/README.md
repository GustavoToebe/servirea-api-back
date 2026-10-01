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
