# Importação de pessoas — CSV e XLSX (F08)

Pessoas → Importar. Requer PESSOA e PESSOA_CRIAR em estrutura, prévia e confirmação. Cria fichas novas, sem alterar/mesclar fichas existentes e sem IA.

## Fluxo e campos

Selecionar arquivo → Ler colunas → escolher aba/mapear campos → Gerar prévia → revisar dados normalizados e erros → Confirmar importação. A tela oferece modelo CSV UTF-8 com BOM. Remova a linha de exemplo antes de usar dados reais.

Nome e papel obrigatórios; CPF, e-mail e telefone opcionais, com opção Não importar. Cada coluna só pode alimentar um campo. Até 30 colunas; as restantes não selecionadas são ignoradas. Cabeçalhos repetidos são identificados pelo índice/posição e exigem escolha explícita quando a sugestão fica ambígua. Sugestões reconhecem nome/nome completo, papel, cpf, email/e-mail e telefone/celular, sem diferenciar caixa/acentos. Títulos arbitrários são aceitos por mapeamento manual. Não converte os valores de papel: RESPONSAVEL, COROINHA, ACOLITO, AMBOS ou MESC.

Até 512 KiB, 100 pessoas, dez abas XLSX, 1.000 linhas físicas (inclusive vazias) e índice de linha XLSX até 10.000. A primeira linha preenchida é cabeçalho e precisa estar nas primeiras 20 linhas. Aba vazia pode ser inspecionada para permitir escolher outra; não pode ser confirmada. CSV usa vírgula ou ponto e vírgula, reconhecidos fora das aspas; aceita aspas duplicadas, mas não células com múltiplas linhas. Até 200 caracteres por célula, nome até 120; caracteres de controle são recusados. XLS e XLSM, senha, células mescladas e vínculos externos não são aceitos.

No XLSX, CPF/telefone devem ser **texto desde a origem**. Valores numéricos nesses campos bloqueiam a linha, mesmo quando a máscara exibe zeros: mudar apenas o formato depois da perda de zeros não recupera o dado. Corrigir na origem. Fórmulas e erros/booleanos da aba bloqueiam a linha, inclusive em colunas ignoradas; não se executam fórmulas nem se usam seus resultados em cache. Usar somente valores em texto. Macro/objetos embutidos/relações externas bloqueiam o arquivo inteiro, inclusive fora da aba selecionada.

## Contrato HTTP

Todos os endpoints recebem multipart/form-data com parte arquivo:

- POST /pessoas/importacoes/estrutura: parâmetro aba (índice 0–9, padrão zero); retorna abas, aba, linhaCabecalho, colunas com indice/titulo e sugestao por campo. Não retorna amostras de contatos.
- POST /pessoas/importacoes/previa: aba e nome/papel/cpf/email/telefone como índices base zero. Campo omitido procura cabeçalho conhecido; -1 ignora opcional. Retorna hash, quantidade, podeConfirmar e linhas (linha/nome/erro/papel/cpf/email/telefone). Erros não criam cadastros. Dados válidos aparecem normalizados para revisar o mapeamento.
- POST /pessoas/importacoes/confirmar: mesmos parâmetros/arquivo, mais chave UUID e hash de 64 dígitos hexadecimais da prévia. Nunca envia o DTO de pessoas de volta como fonte confiável.

Hash SHA-256 inclui bytes + versão do contrato + aba + **mapeamento resolvido**. Trocar arquivo/aba/mapeamento invalida prévia e chave na tela. A confirmação exige o mesmo contexto; divergência retorna 409. O campo histórico hash_arquivo agora representa essa impressão do lote, não só os bytes; nomes de colunas do banco foram preservados. Sem migration nova (continua V074). Backend/frontend devem ser implantados juntos; uma prévia gerada em versão anterior precisa ser refeita.

## Atomicidade, duplicidades e cotas

Prévia não grava pessoas, arquivo ou consumo. Normaliza CPF/telefone por Formatos e valida DTO/contatos. Consulta possíveis duplicados por nome exato sem diferença de caixa ou CPF no lote e no banco **da paróquia atual**, por projeção JPQL. Homônimos exigem revisão manual; não há ignorar automaticamente, atualizar ou mesclar. Corrija/remova duplicados no arquivo e gere outra prévia. Nenhuma busca usa nomes de outras paróquias.

Confirmação reserva a raiz da paróquia antes do domínio, relê o arquivo, confere hash/opções, recupera conclusão pela chave ou revalida todo o lote e duplicidades atuais. Persiste pessoas, auditoria e unidade importacoes_mes na mesma transação. Erro de validação, duplicidade, cota de pessoas/voluntários/importações ou persistência desfaz tudo. Mesma chave/contexto devolve conclusão sem duplicar/cobrar outra unidade; chave reutilizada com contexto diferente retorna 409. Repetições concorrentes serializam na raiz. Uma importação mensal por lote, não por pessoa ou documento.

Voluntário recebe perfil correspondente, sem autorização WhatsApp, relações, cuidado, endereço ou funções inventados. Atualizar depois na ficha com permissões próprias. Browser preserva arquivo/chave após falha de rede para repetir; limpa referências após sucesso/destruição e invalida caches somente no sucesso. Arquivo não vai para Storage, não é salvo no registro da importação e não entra na cota de bytes; transporte multipart pode usar temporário do servidor conforme configuração. O registro retém apenas chave/hash/quantidade/competência/instante e auditoria; não eliminar para liberar cota.

## Leitura de XLSX e limites de segurança

Apache POI 5.5.1 pelo modelo de eventos SAX (XSSFReader/XSSFSheetXMLHandler); não monta workbook completo nem usa avaliador de fórmulas. Dependência fixada após consultar [distribuição oficial](https://poi.apache.org/download.html), [orientações de segurança](https://poi.apache.org/security.html) e [API de eventos](https://poi.apache.org/apidocs/dev/org/apache/poi/xssf/eventusermodel/XSSFReader.html). Essa camada não equivale a uma sandbox de processos.

ZipSeguro valida diretório central, inflação e profundidade antes de POI; preflight lê novamente entradas sob limites, sem confiar só nos tamanhos declarados: 128 entradas, 16 MiB por entrada, 32 MiB total, nomes de entrada sem repetição. XML antes do pacote POI: DTD/entidades externas desativados pelo XMLHelper, até 20.000 elementos/parte, profundidade 32, 30 atributos/elemento, 1.000 caracteres/atributo e 1 MiB de texto/parte. Sem relaxar as proteções padrão de ZIP do POI. Arquivos legítimos muito complexos/compactados também podem ser recusados: exportar uma aba simples só com dados. Sem rede para resolver relações da planilha e sem conteúdo do arquivo em logs.

## Limites restantes

Relações/responsáveis, edição/mesclagem de pessoas existentes, histórico detalhado por linha, documentos gerais e lotes maiores/worker continuam fora desta rodada. Mesclagem precisará de revisão explícita de conflitos, permissões e auditoria antes de existir. F08 mínimo CSV/XLSX/mapeamento/validação/idempotência foi entregue; evolução ampla continua parcial.

Homologar Excel/LibreOffice reais, temas claro/escuro, tela pequena, teclado nos seletores e dois perfis/paróquias em staging. Migrations/produção não foram alteradas por esta entrega.
