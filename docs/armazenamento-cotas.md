# Armazenamento de arquivos vinculados

F26, V057: fotos de voluntários, inscrições públicas e eventos guardam foto_tamanho_bytes. Novos uploads usam bytes efetivamente lidos. Não são somados valores fornecidos pelo cliente. Anexos de comunicados usam o tamanho persistido enquanto conteudo não é NULL; sua limpeza existente libera o consumo e mantém metadados no histórico.

## Cota e contagem

`armazenamento_mb` nos direitos é inteiro não negativo. 1 MB significa 1.048.576 bytes. A API /minha-conta/consumo mantém o código, mas retorna usado/limite/disponivel em bytes, com unidade=bytes; a tela converte para MB. Ausência mantém compatibilidade sem teto. Zero impede crescimento. Dados acima de um plano reduzido continuam acessíveis.

Caminho compartilhado pela inscrição e pelo voluntário aprovado conta uma vez; aprovação copia o metadado. Substituição recebe crédito da foto anterior somente quando não há outra referência. Sob downgrade, redução do consumo é permitida. Fotos de eventos removidas deixam de contar. Fotos de inscrições ainda mantidas continuam contando.

Uploads/novos anexos reservam a paróquia antes do domínio. Projeção é conferida antes do upload externo; validação após flush reverte a transação inteira se exceder. Cotas de arquivo e cadastro coexistem. Fotos de perfil usam UUID no nome para evitar colisão. Cadastros sem teto de armazenamento não percorrem os metadados de fotos; consulta de consumo sempre calcula os arquivos. Não há contador duplicado.

## Fotos anteriores à V057

NULL significa tamanho desconhecido, não zero. Caminhos desconhecidos aparecem em estado INVENTARIO_PENDENTE, pendentes>0 e disponivel=NULL. Consumo conhecido é parcial. Plano com teto recusa novos arquivos com 409 ARMAZENAMENTO_INVENTARIO_PENDENTE até conferir; edição sem upload continua possível. Plano sem teto mantém uploads compatíveis e conserva o aviso.

POST /minha-conta/armazenamento/conferir?inicio=0, com PERM_PAROQUIA_ALTERAR, não aceita caminhos nem tamanhos do navegador. Seleciona até 5 caminhos referenciados pela paróquia autenticada e consulta HEAD autenticado no Supabase, sem baixar imagens. HTTP ocorre fora da transação; cada gravação usa transação curta, trava da paróquia e atualização somente de metadado ainda NULL. Auditoria participa dessa transação. Nenhuma URL/caminho de foto é retornada.

Resposta: conferidos, falhas, pendentes, proximoInicio. Use proximoInicio na chamada seguinte; falhas no começo não impedem avançar. Posição negativa/acima de 1.000.000 é recusada. É uma ação manual da tela, sem loop ou job de varredura. Falha/configuração ausente/tamanho ausente não vira zero; falta de arquivo no provedor requer corrigir a referência/configuração antes de concluir. Metadados preenchidos não são reconsultados automaticamente.

O endpoint HEAD está documentado no [código oficial do Supabase Storage](https://github.com/supabase/storage/blob/master/src/http/routes/object/getObjectInfo.ts). Conferir compatibilidade em staging com o provedor efetivamente implantado; aqui os testes usam servidor/mock local, não chaves reais.

## Limites do que é medido

Esta é uma cota do produto para arquivos **referenciados pelo aplicativo**, não a fatura física do Supabase. Exclui arquivos órfãos, versões antigas e arquivos inseridos diretamente no bucket. Fotos externas podem ficar órfãs após rollback ou troca; esta etapa não implanta coleta de lixo durável. Uploads ainda seguem o fluxo existente dentro da transação de negócio; a trava pode esperar o HTTP. Separar reserva/upload/confirmação com recuperação durável é melhoria operacional pendente, sem afirmar que os dois sistemas tenham commit atômico.

O campo legado uso.armazenamento_mb da consulta HMAC de instância continua devolvendo 0 como placeholder; não usar esse campo para faturamento ou inferir espaço livre. A medição real desta etapa é a consulta local /minha-conta/consumo. Integrar esse consumo ao painel agregado da Central permanece pendente (F06/F26).

A cota de bytes não limita quantidade mensal de mensagens ou importação de documentos nem tamanho do banco inteiro. Cotas mensais da fila e lotes CSV de pessoas estão separadas em [envios/importação](cotas-envios-importacao.md). Payload por arquivo/proxy continua com seus limites próprios. XLSX e documentos gerais continuam pendentes.

## Verificação

PostgreSQL/Testcontainers: isolamento, crédito de substituição, foto compartilhada, espaço exato, duas disputas concorrentes, falhas de inventário e avanço de lote, conferência fora da transação, permissões, anexos liberados e rejeição de upload público/evento/anexo sem cota. Servidor HTTP mock verifica HEAD, Authorization/apikey, Content-Length e recusa de tamanho desconhecido.
