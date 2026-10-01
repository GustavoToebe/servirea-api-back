# Cotas de cadastro do plano

Primeira parte de F26: `pessoas`, `voluntarios` e `usuarios`, inteiros não negativos nos direitos locais enviados pela Central. Esses recursos agora anunciam aplicado=true. Zero bloqueia crescimento; ausência mantém compatibilidade sem limite configurado. Formato inválido retorna 503 COTAS_INVALIDAS.

Pessoas incluem todos os cadastros, mesmo inativos; dois papéis contam uma vez. Voluntários contam cadastros com esse papel, mesmo inativos. Usuários contam vínculos ATIVO da paróquia, inclusive convites e usuários/perfis globalmente inativos; só desativar o vínculo libera vaga. O primeiro administrador provisionado é preservado mesmo em plano insuficiente.

Criação/alteração de pessoa (incluindo responsáveis criados dentro da ficha), aprovação de inscrição e criação/reativação de vínculo de usuário reservam a paróquia antes de carregar os dados. Após flush, consumo real é comparado com o anterior. Crescimento acima da cota lança 409 COTA_EXCEDIDA e reverte toda a transação; convites não são enviados em transação revertida. Aplicar direitos usa a mesma trava. Não há contador duplicado nem nova tabela. Plano reduzido preserva dados e permite edição sem crescimento; não apaga pessoas automaticamente.

GET `/minha-conta/consumo`, com PERM_PAROQUIA, retorna plano/versão/instantes e itens codigo/nome/usado/limite/disponivel/estado, sem dados pessoais. Estados: SEM_LIMITE_CONFIGURADO, DISPONIVEL, ATENCAO (80%), ATINGIDO e EXCEDIDO. Consumo é um snapshot local; não depende de disponibilidade da Central e não inventa zero quando falha.

Armazenamento de fotos vinculadas e anexos retidos agora aplicado: [detalhes](armazenamento-cotas.md). Importação de documentos, mensagens e funcionalidades comerciais ainda não têm cotas aplicadas. Não apresentar esses recursos como cobrança por consumo implementada. Novos caminhos que criem pessoas ou ativem usuários precisam usar a mesma reserva; manter ordem de lock paróquia antes do domínio.
