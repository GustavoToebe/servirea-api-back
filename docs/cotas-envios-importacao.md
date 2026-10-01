# Cotas mensais de envios e importação CSV

V058–V059, F26 e primeira parte de F08. Sem IA ou cobrança automática por excedente.

## Recursos e mês

`emails_mes`, `whatsapp_mes` e `importacoes_mes`: inteiros não negativos nos direitos enviados pela Central, com aplicado=true no catálogo. Omissão mantém compatibilidade sem teto; zero bloqueia crescimento. Configuração inválida nunca vira ilimitada. Competência é o mês civil em America/Sao_Paulo, sem relação com o aniversário/vencimento da contratação. Minha conta retorna estes itens com competencia=AAAA-MM; usado/limite/disponivel são unidades, não bytes.

## Envios da fila

Uma unidade por destinatário/mensagem lógica na primeira reserva para tentar enviar. Inclui comunicados manuais, confirmações e lembretes de eventos, via EnvioAvulso. Unidade/competência ficam na mesma transação da posse; HTTP continua fora da transação. Ordem de lock da reserva: paróquia → janela → comunicado → destinatário. Conclusão mantém janela → comunicado → destinatário; não tenta adquirir paróquia depois desses locks. Aplicação de direitos e primeiras reservas compartilham a trava da paróquia.

Ao atingir a cota, o destinatário permanece PENDENTE com aviso Aguardando disponibilidade da cota mensal de envios, sem incrementar tentativas/falhas ou chamar o provedor. Reavalia após cinco minutos (não imediatamente após ampliar o plano). Próximo mês/limite ampliado permite uma primeira tentativa. Mensagem com autorização WhatsApp revogada é recusada antes de gastar unidade.

Falha, crash, expiração ou Reenviar falhas da mesma mensagem preservam a competência: não gastam outra unidade, inclusive se a repetição ocorrer em outro mês. Criar um comunicado novo gera mensagens novas e novas unidades. Não se devolve unidade após falha ou downgrade. A V058 reconstrói a competência de mensagens antigas com enviado_em confirmado; falhas antigas sem instante conhecido só entram quando reservadas novamente. Tamanho do histórico e fila acumulada não são limitados nesta etapa. Não apagar registros contabilizados para liberar cota; eventual retenção deve preservar medição durável.

Modo log/mock também contabiliza a tentativa lógica. ENVIADO significa aceitação do provedor. Esta métrica não garante entrega/leitura e não representa o custo físico das tentativas no provedor. A garantia pelo menos uma vez e limites de idempotência seguem docs/fila-comunicados.md. E-mails de login/convite/recuperação e teste técnico direto do WhatsApp ficam fora dos recursos da fila; não os apresentar como cobertos por estes tetos. Não há integração de consumo agregado à Central nesta etapa.

## Importação de pessoas por CSV

Pessoas → Importar CSV, com PERM_PESSOA e PERM_PESSOA_CRIAR. POST /pessoas/importacoes/previa e /confirmar são multipart com arquivo. Confirmação recebe chave UUID e hash SHA-256 exibido pela prévia; server recalcula o hash, relê e valida todos os dados, sem confiar no DTO da prévia. Chave é própria do lote e por paróquia: mesma chave+arquivo devolve o resultado concluído, sem novos cadastros/unidades; mesma chave+outro arquivo retorna 409. Duas confirmações concorrentes serializam na raiz da paróquia. Não há worker assíncrono ou lote parcial.

Formato UTF-8, BOM permitido, até 512 KiB e 100 pessoas; cabeçalho exato nome;papel;cpf;email;telefone, separador ponto e vírgula ou vírgula. Aspas e aspas duplicadas são aceitas; células com múltiplas linhas não são. Papel RESPONSAVEL ou COROINHA/ACOLITO/AMBOS/MESC. CPF/e-mail/telefone opcionais; documentos e telefones passam por Formatos, e DTO por Bean Validation. Nome até 120 caracteres e célula até 200. Papel de voluntário cria perfil correspondente ativo, sem consentimento WhatsApp, funções, relações, cuidado ou endereço inventados. Atualizar esses dados depois pela ficha com as permissões pertinentes.

Prévia não grava arquivo, pessoas ou consumo. Mostra erro por linha e possíveis duplicidades por nome exato (sem diferença de caixa) ou CPF no arquivo/banco da paróquia. Consulta metadados em lote via JPQL, sem carregar fichas completas; nomes iguais podem ser homônimos e exigem revisão manual. Não sobrescreve nem une cadastros. Confirmação revalida duplicidades sob trava. Lote inválido/cota de pessoas/voluntários ou falha de persistência reverte tudo, inclusive auditoria e unidade de importação.

Uma unidade em importacoes_mes por lote confirmado, até 100 pessoas; não por linha/arquivo/documento armazenado. Registra UUID/chave/hash/quantidade/competência/instante e auditoria, sem CSV ou nomes/contatos no registro da importação. Arquivo fica temporário na requisição/browser e não entra no Storage nem na cota de bytes. Registros de conclusão não podem ser apagados arbitrariamente, pois garantem idempotência e contagem. Não interpreta fórmulas, macros ou XLSX; não há módulo de importação de documentos gerais. O front conserva a chave ao repetir após erro de rede e invalida caches de pessoas/voluntários somente após sucesso.

## Validação e pendências

Testes: cap por canal/paróquia, zero, concorrência, provider fora da transação, falha/reenvio sem duplicação, limite ampliado, virada do mês em Brasília, formato inválido, endpoints/permissões, prévia sem escrita, lote atômico, limite de pessoas/importação, duplicidades, encoding/tamanho, hash alterado e chave concorrente/isolada. Esquema gerado em PostgreSQL 17 descartável; migrations não executadas em produção.

F26 ainda parcial: funcionalidades comerciais, consumo agregado/exportação, adicionais específicos e uso físico dos provedores. F08 ainda parcial: XLSX, mapeamento livre, vínculos/responsáveis, edição/mesclagem e lotes maiores assíncronos. Homologar provedores e telas em staging antes de deploy. Preços e franquias concretas continuam decisão comercial.
