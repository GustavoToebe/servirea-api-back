# Mural: público e confirmação de leitura — F13

Contrato de 02/10/2026, V067. Descreve o código da branch; implantação não confirmada.

Aviso PUBLICADO pode ser dirigido a TODOS ou SELECIONADOS (1–100 contas com vínculo ativo na mesma paróquia). ARQUIVADO permanece acessível a editores. Leitor só recebe avisos publicados para seu público; tentativa por ID fora do público retorna 404. Editor com MURAL_CRIAR/ALTERAR vê os registros e a seleção, sem conceder ao leitor o diretório de destinatários. Contas da paróquia são usuários do sistema, não todas as pessoas cadastradas. Consulta de destinatários retorna até 30 identificadores/nomes por busca. Remoção/inativação do vínculo segue a autenticação existente; salvar exige vínculos ativos.

Abrir aviso não confirma leitura. PUT `/mural/avisos/{id}/leitura` recebe `{ "versao": 3 }`, exige MURAL e MURAL_CONFIRMAR, conta pessoal da paróquia e recurso MURAL no plano. Repetição na mesma versão é idempotente; edição invalida a confirmação anterior e exige nova leitura. Versão antiga recebe 409. Há um registro de última leitura por conta/aviso, não uma trilha completa de todas as leituras. Editores recebem quantidade de confirmações da versão atual; leitor recebe apenas seu estado, sem identidades/contagens de terceiros. A contagem usa agregação no banco e consulta pessoal em lote, sem carregar todas as leituras em memória.

Salvar `/mural/avisos` mantém titulo/descricao/status/prazo/versao e acrescenta publico/destinatarios. TODOS descarta destinatários; versão protege edição. Escritas bloqueiam a paróquia, usam @TenantId e auditoria transacional. V067 cria destinatários/leituras com unicidade e FKs de paróquia, RLS e revogação. Templates exibem texto, sem HTML executável.

Homologar duas contas da mesma paróquia, terceiro usuário fora do público e outra paróquia; confirmar duas vezes, editar e confirmar novamente; testar 409 e arquivamento. Perfis personalizados precisam MURAL_CONFIRMAR. Notificações e centro completo de entregas continuam pendentes (F13/F05); nenhum envio é disparado pelo mural.
