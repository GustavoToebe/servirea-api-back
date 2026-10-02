# Distribuição por regras — F04

Sugere pessoas para vagas vazias de uma escala em RASCUNHO. Não usa IA: o motor (`escala/MotorDistribuicao`) é determinístico e a coordenação decide o que aplicar. Sem migration (continua V076).

## Regras
Elegibilidade: voluntário ativo, função habilitada, sem indisponibilidade na data/período, dentro da disponibilidade positiva quando existir e, se `exigirResposta`, com resposta de disponibilidade do mês (sem restrição ou datas indisponíveis cadastradas). Colisão: mesmo evento ou mesmo horário, e intervalo mínimo em dias contra vagas desta e de outras escalas não canceladas. Limite de participações por pessoa (1 a 20) conta vagas já existentes e sugestões anteriores. Vagas com menos elegíveis são preenchidas primeiro; entre elegíveis vence a menor carga e o UUID desempata. Linhas de referência e vagas já ocupadas nunca recebem sugestão.

## API
POST /escalas/{id}/distribuicao/previa com `{regras:{maximoPorPessoa,intervaloDias,exigirResposta}}`; exige `VAGA_DISTRIBUIR`, não grava. Devolve versão da escala, sugestões com explicação por vaga e conflitos com contagem de descartes por motivo. Alocação existente que viola as regras aparece como conflito `alocacaoExistente` e marca a prévia como `bloqueado`.

POST /escalas/{id}/distribuicao/aplicacao com `{versao,regras,escolhas:[{vagaId,pessoaId}]}`; exige `VAGA_DISTRIBUIR` e `VAGA_ALOCAR`, até 500 escolhas. Trava a escala, confere a versão (409 se mudou), recalcula com os dados atuais e recusa (409) qualquer escolha que o motor não produza mais, vaga repetida (400) ou prévia bloqueada. Grava só as escolhas enviadas, zera presença/resposta, incrementa a versão da escala e registra DISTRIBUICAO na auditoria. Escala fora de RASCUNHO responde 409; outra paróquia recebe 404. Limite de 600 vagas por cálculo.

## Limites
Não há critérios configuráveis além dos três campos, nem balanceamento entre meses, preferência por irmãos ou notificação das pessoas sugeridas. A carga considera apenas a janela de datas da escala ampliada pelo intervalo. Os perfis existentes precisam receber `VAGA_DISTRIBUIR` explicitamente; ADMIN já tem o catálogo completo. Homologar com volume e disponibilidades reais antes de usar na paróquia.
