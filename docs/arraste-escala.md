# Edição da escala por arraste e teclado — F18

Entrega mínima de 02/10/2026. Apenas frontend; usa o contrato de salvamento da escala existente.

No rascunho, buscar voluntário ativo e arrastar para a vaga; alternativa sem mouse: selecionar o botão da pessoa e acionar “Aplicar pessoa selecionada” na vaga. A seleção usa os voluntários já carregados, com até 30 resultados por busca. Não é novo seletor remoto/paginação de voluntários (T05 permanece parcial). Interface aceita somente arraste iniciado neste componente, não IDs copiados de outra aplicação. Escala finalizada, referência, salvamento em andamento ou ausência de ESCALA_CRIAR/ALTERAR impedem a ação.

A ação valida pessoa ativa, função habilitada, pessoa duplicada na mesma celebração, indisponibilidade declarada disponível no apoio mensal e outra celebração do mesmo rascunho na mesma data/horário. Substituir exige confirmação; depois do diálogo, vaga e condições são conferidas novamente. Cancelamento preserva a pessoa anterior. Ação marca rascunho como alterado e exige salvar explicitamente; não grava automaticamente nem modifica escala de referência.

Limites: função/indisponibilidade/conflitos desta interação são verificações da tela com os dados carregados. API existente preserva autorização, isolamento, versão e rejeição de duplicação na mesma celebração; esta entrega não acrescenta verificação global de conflitos em todas as escalas nem transforma indisponibilidade em bloqueio universal no servidor. Edição manual existente mantém sua regra. Revalidar dados atualizados antes de finalizar; F04 assistente automático não foi implementado.

Homologar arraste e alternativa de teclado em grades diária/semanal/mensal, tela pequena, função inválida, inativo, duplicação, indisponibilidade e substituição cancelada. Testar duas telas concorrentes no salvamento existente. Nenhuma mensagem ou confirmação de presença é enviada.
