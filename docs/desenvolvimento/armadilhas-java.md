# Armadilhas de implementação Java

Leia antes de alterar persistência, DTOs, segurança, storage ou integração. Estas regras complementam [AGENTS.md](../../AGENTS.md).

## Armadilhas já pagas (não repetir)
- `open-in-view: false` → acessar coleção lazy no controller dá `LazyInitializationException`. Use
  `@EntityGraph`/`JOIN FETCH` no repositório (ex.: `VoluntarioRepository.findById`).
- Criteria/`JOIN FETCH`/`@EntityGraph` em **duas** coleções `List` da mesma raiz (`emails` + `telefones`)
  explode em `MultipleBagFetchException` ou, no grafo, simplesmente não carrega. Não fetchar as duas;
  inicialize as coleções dentro do `@Transactional` do service (`PessoaService.buscarPorId`,
  `EscalaService.buscarPorId`, `InscricaoService.buscar`).
  `default_batch_fetch_size: 50` evita N+1. Grafo com várias bags numa query dá "Could not generate fetch".
- Todo `XResponse.de(entidade)` roda no controller, **fora** da transação: o service devolve a entidade
  já inicializada. Endpoint novo ganha teste HTTP real em `FluxoHttpIntegrationTest` (teste de service não pega isso).
- `Pessoa.voluntario` (lado `mappedBy` do 1:1 `@MapsId`) precisa de `@Fetch(FetchMode.JOIN)`: sem isso,
  inicializar o proxy de um responsável sem perfil dá `EntityFilterException` (o `@TenantId` "filtra" a linha
  inexistente). `@NotFound(IGNORE)` não resolve.
- Relações de pessoa: `PessoaRequest`/`PessoaResponse` têm `responsaveis` e `dependentes` separados; em cada item
  `parentesco` = o que a **outra** pessoa é. Não voltar para uma lista só (quem tem os dois papéis não salvava).
- `pessoaRepository.findPorEmailPrincipal` devolve **lista**: e-mail principal não é único (criança usa o da mãe).
- CSRF: `SecurityConfig.exigeCsrf` dispensa o token para `Authorization: Bearer` fora de `/auth/**`.
  Não remover — o Angular não manda `X-XSRF-TOKEN` para URL absoluta.
- Rate limit / IP: use `ClientIp.de(request)` (= `getRemoteAddr()`). **Nunca** ler
  `X-Forwarded-For` no código — o cliente forja e esvazia o limitador. Em prod,
  ao ativar interpretação de headers encaminhados, conferir `internal-proxies`
  e a configuração efetiva do profile. O proxy tem que **sobrescrever** o header (`$remote_addr`), não
  concatenar o valor que o browser mandou.
- "Apaga tudo e reinsere" (`clear()` + `orphanRemoval`) com índice único parcial (responsável principal):
  `entityManager.flush()` logo depois do `clear()`.
- Capturar `DataIntegrityViolationException` de um INSERT exige `saveAndFlush`, não `save`.
- `RuntimeException` marca a transação inteira como rollback; um efeito que precisa sobreviver vai para um
  método de **outro bean** com `REQUIRES_NEW` (ex.: `RefreshTokenRepository.revogarTodosAtivosDoUsuario`).
- JPQL `(:p IS NULL OR ...)` quebra no Hibernate 7 + Postgres; filtros opcionais via `Specification`.
- Jackson: a aplicação usa **Jackson 3** (`tools.jackson.*`, bean `JsonMapper`). Jackson 2
  (`com.fasterxml.jackson.databind`) só existe por causa do `jjwt-jackson`, então não importe no código.
- Spring Boot 4 é modular: Flyway exige `spring-boot-starter-flyway`; `@AutoConfigureMockMvc` fica em
  `org.springframework.boot.webmvc.test.autoconfigure` (artefato `spring-boot-webmvc-test`);
  `RestClient.Builder` é bean próprio (`RestClientConfiguration`); `HibernatePropertiesCustomizer` fica em
  `org.springframework.boot.hibernate.autoconfigure`.
- `GlobalExceptionHandler` precisa relançar `AccessDeniedException`/`AuthenticationException`, senão o
  `@PreAuthorize` negado vira 500.
- Supabase Storage: enviar **os dois** headers `Authorization: Bearer` e `apikey`; não usar template `{caminho}` na URI (codifica `/`).
- Foto: a extensão do caminho sai de `ExtensaoDeFoto` (o Content-Type), nunca do nome do arquivo. Bytes que começam com `PK` não são imagem.
- Ativo do voluntário não muda no `POST`/`PUT /pessoas` (o campo no JSON é ignorado). Só `PATCH /voluntarios/{id}/ativo`, com `PERM_PESSOA_ATIVAR_INATIVAR`.
- Anexo DOCX/XLSX passa por `ZipSeguro` antes de gravar: teto de entradas, tamanho descompactado e um nível de zip dentro do outro. Não há teto de razão descompactado/compactado: planilha com linhas repetidas pode passar de 100:1 e continua válida. Não inflar o anexo fora dessa classe.
- Enum nativo do Postgres: `@Enumerated(STRING)` + `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`; array de enum:
  `@JdbcTypeCode(ARRAY)` + `@ColumnTransformer(write = "?::tipo[]")`. Em JPQL, enum **sempre por
  parâmetro** (`c.status = :status`), nunca literal (`Tenant.Status.ATIVO`): o literal vira
  `cast(... as status)` (nome da classe Java) e quebra com `type "status" does not exist`.
- Coluna `smallint` não valida contra campo `int` (`ddl-auto: validate`); use `integer`.
- SQL manual em produção vai pelo `psql`, não pelo SQL Editor do Supabase: o botão "Run and enable RLS"
  corrompe blocos `DO $$`. Em PL/pgSQL, prefira `var := (SELECT ...)` a `SELECT ... INTO var` (o editor lê como
  criação de tabela).
- `inscricoes.aprovado_por`/`rejeitado_por` e `escalas.created_by` apontam para `public.usuario` (V023); no
  banco antigo eram ids do Supabase Auth. Migrar dado antigo exige criar o `usuario` com o mesmo id antes.
- Segredo nunca vai para `.env.example` (é versionado): só o nome da variável, valor vazio.
- Tabela nova em `public`: `ENABLE ROW LEVEL SECURITY` sem policy (V039 apagou as policies antigas e tirou os
  grants de anon/authenticated; o teste da V039 quebra se sobrar tabela sem RLS), senão a Data API do Supabase
  (chave publicável) lê/grava. A API Java é dona das tabelas e não é afetada.
- Entidade com **chave preenchida à mão** (`integracao_operacao`, `integracao_nonce`): o `save` do Spring
  Data faz `merge` (UPDATE) e a PK nunca esbarra — nonce repetido passava. Use `Persistable` com `isNew`
  (`IntegracaoOperacao`) ou `INSERT ... ON CONFLICT DO NOTHING` contando linhas (`IntegracaoNonceRepository`).
- `/integracao/**` **não** é `permitAll`. O filtro decide pelo caminho decodificado (`servletPath`) e assina o
  cru; o controller exige `PERM_INTEGRACAO`, então um caminho que escape do filtro (`/%69ntegracao/...`) dá 401.
- Bloqueio por atraso é decisão da Central; o app obedece `acesso_liberado` da cópia local. Operador do SaaS
  não existe no app; billing comercial pertence à Central (corte realizado na V051).

