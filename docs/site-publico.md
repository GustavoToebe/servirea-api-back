# Página pública da paróquia — F20

Código de 02/10/2026, V070. Nenhum deploy de site/produção foi executado.

Menu Página pública, rota administrativa `/site-paroquia`. Rascunho: título/apresentação, endereço, horários e contato que possam ser públicos, até 30 blocos manuais de aviso/evento com título/texto/quando/local. Não importa pessoas, contatos privados, escala ou mural automaticamente. Conteúdo é texto escapado por Angular, sem HTML executável. Revisar cuidadosamente os dados que a equipe decide publicar.

Salvar rascunho não altera página publicada. “Publicar rascunho salvo” exige confirmação explícita e versão atual, copia apenas o DTO público para snapshot separado e registra auditoria. Alterações não salvas bloqueiam publicação na tela. Edição posterior deixa a versão pública anterior até nova publicação. Retirar do ar limpa publicação e preserva rascunho. Guard de saída protege alterações locais; mudanças concorrentes recebem 409, sem repetição automática.

Permissões SITE (consulta), SITE_EDITAR (rascunho), SITE_PUBLICAR (publicar/retirar). PUT `/site-paroquia`: `{versao,dados}`. POST `/site-paroquia/publicar`: `{versao,confirmar:true}`. POST `/site-paroquia/despublicar`: `{versao}`. Primeira gravação versão 1; ausência inicial 0. Escritas bloqueiam paróquia e auditam na mesma transação. V070 tabela única por tenant, @TenantId, versão, limites de texto, RLS/revogação.

Rota pública frontend `/p/:slug`, fora do guard de autenticação; GET `/public/paroquias/{slug}` da API retorna somente snapshot publicado. Define TenantContext antes de nova transação e restaura contexto ao terminar. Slug inexistente, ausência/retirada de publicação ou paróquia não liberada retorna 404 genérico. Resposta `Cache-Control: no-store`; não expõe rascunho, IDs de pessoas ou configurações internas. Limitador local de 120 consultas/IP/minuto e mapa de até 10.000 IPs, sem confiar em IP enviado em header; não é contador distribuído. Proxy/CDN devem ser homologados para múltiplas réplicas e IP real.

Homologar publicação/edição/retirada, conta sem ação, outra paróquia, 409, erro de rede, navegação entre slugs e textos com `<script>` sem execução. Snapshot publicado é conteúdo deliberadamente público; retirada não recupera cópias que terceiros já tenham feito. Servir frontend/backend atualizados em staging é etapa de implantação separada.
