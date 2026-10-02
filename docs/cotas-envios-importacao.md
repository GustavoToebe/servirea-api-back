# Cotas mensais de envios e importação CSV

V058–V059, F26 e primeira parte de F08. Sem IA ou cobrança automática por excedente.

## Recursos e mês

`emails_mes`, `whatsapp_mes` e `importacoes_mes`: inteiros não negativos nos direitos enviados pela Central, com aplicado=true no catálogo. Omissão mantém compatibilidade sem teto; zero bloqueia crescimento. Configuração inválida nunca vira ilimitada. Competência é o mês civil em America/Sao_Paulo, sem relação com o aniversário/vencimento da contratação. Minha conta retorna estes itens com competencia=AAAA-MM; usado/limite/disponivel são unidades, não bytes.

## Envios da fila

Uma unidade por destinatário/mensagem lógica na primeira reserva para tentar enviar. Inclui comunicados manuais, confirmações e lembretes de eventos, via EnvioAvulso. Unidade/competência ficam na mesma transação da posse; HTTP continua fora da transação. Ordem de lock da reserva: paróquia → janela → comunicado → destinatário. Conclusão mantém janela → comunicado → destinatário; não tenta adquirir paróquia depois desses locks. Aplicação de direitos e primeiras reservas compartilham a trava da paróquia.

Ao atingir a cota, o destinatário permanece PENDENTE com aviso Aguardando disponibilidade da cota mensal de envios, sem incrementar tentativas/falhas ou chamar o provedor. Reavalia após cinco minutos (não imediatamente após ampliar o plano). Próximo mês/limite ampliado permite uma primeira tentativa. Mensagem com autorização WhatsApp revogada é recusada antes de gastar unidade.

Falha, crash, expiração ou Reenviar falhas da mesma mensagem preservam a competência: não gastam outra unidade, inclusive se a repetição ocorrer em outro mês. Criar um comunicado novo gera mensagens novas e novas unidades. Não se devolve unidade após falha ou downgrade. A V058 reconstrói a competência de mensagens antigas com enviado_em confirmado; falhas antigas sem instante conhecido só entram quando reservadas novamente. Tamanho do histórico e fila acumulada não são limitados nesta etapa. Não apagar registros contabilizados para liberar cota; eventual retenção deve preservar medição durável.

Modo log/mock também contabiliza a tentativa lógica. ENVIADO significa aceitação do provedor. Esta métrica não garante entrega/leitura e não representa o custo físico das tentativas no provedor. A garantia pelo menos uma vez e limites de idempotência seguem docs/fila-comunicados.md. E-mails de login/convite/recuperação e teste técnico direto do WhatsApp ficam fora dos recursos da fila; não os apresentar como cobertos por estes tetos. Não há integração de consumo agregado à Central nesta etapa.

## Importação de pessoas por CSV/XLSX

Contrato atualizado de F08 em [importacao-pessoas.md](importacao-pessoas.md): estrutura/abas, mapeamento por índice, prévia dos dados normalizados, revalidação integral e hash de arquivo/aba/mapeamento. Uma unidade em importacoes_mes por lote confirmado até 100 pessoas, sem gravar arquivo no Storage nem mesclar fichas. Mesma chave/contexto recupera conclusão. Mesma reserva e atomicidade de pessoas/auditoria/unidade, com duplicidades bloqueadas e escopo da paróquia.

## Validação e pendências

Testes: cap por canal/paróquia, zero, concorrência, provider fora da transação, falha/reenvio sem duplicação, limite ampliado, virada do mês em Brasília, formato inválido, endpoints/permissões, prévia sem escrita, lote atômico, limite de pessoas/importação, duplicidades, encoding/tamanho, hash alterado e chave concorrente/isolada. Esquema gerado em PostgreSQL 17 descartável; migrations não executadas em produção.

F26 ainda parcial: funcionalidades comerciais, consumo agregado/exportação, adicionais específicos e uso físico dos provedores. F08 mínimo CSV/XLSX/mapeamento/validação entregue; vínculos/responsáveis, edição/mesclagem e lotes maiores assíncronos continuam pendentes. Homologar provedores e telas em staging antes de deploy. Preços e franquias concretas continuam decisão comercial.
