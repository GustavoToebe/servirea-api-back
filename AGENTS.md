# AGENTS.md — servire-api

Backend Java do **Servire** (SaaS multi-tenant de gestão paroquial: voluntários/coroinhas,
escalas de missa, inscrições públicas). Este repo é o **`servire-api-back`**; o Angular
é o irmão **`servire-api-front`** (outro git — não misturar). Substitui aos poucos o
acesso direto do front ao Supabase. Idioma do código, comentários, mensagens de erro
e commits: **português**.

- Referência de produto/arquitetura: `plano_mestre_servire_v2_mvp_baixo_custo.md` (~4000 linhas).
  O código cita "seção N" desse arquivo o tempo todo — use `grep -n "^# N\." ` para achar.
- Estado atual (módulos, contrato do front, como rodar): `README.md`.
- Histórico de fases, decisões e bugs reais (#1–#28): `HISTORICO.md`. Atenção: a
  numeração de "Fase 11" dali (permissões/faltas/disponibilidade/auditoria/e-mail)
  **não** é a "FASE 11 — Backoffice" da seção 111 do plano mestre.

## Manter este arquivo atualizado
Este é o arquivo de instruções compartilhado entre ferramentas de IA (Cursor, Codex, Copilot etc.);
o `CLAUDE.md` só importa este (`@AGENTS.md`). Edite **só aqui**.
- Mudança que invalida algo daqui (migration nova, pacote novo, comando novo, armadilha resolvida)
  atualiza este arquivo **junto com a funcionalidade**.
- Manter curto: só o que evita erro de quem vai mexer no código. Estado atual no
  `README.md`; histórico detalhado no `HISTORICO.md`.
- Commit e push direto na `main` a cada alteração, com testes e build verdes (decisão de 26/09/2026,
  igual aos outros três repositórios). Branch só quando pedido explicitamente.

## Stack
Java 21 · Spring Boot **4.1.1** (Spring Framework 7, Security 7, Jakarta EE 11) · Hibernate 7.4 ·
PostgreSQL 17 (Supabase em prod: 17.6 em 25/09/2026) · Flyway · JJWT 0.13 · Testcontainers 2.x · Maven (sem wrapper).
Virtual Threads ligadas; Spring MVC síncrono (nada de WebFlux).

## Comandos
```powershell
mvn clean verify                                              # build + todos os testes (precisa do Docker Desktop aberto)
mvn test -Dtest=VoluntarioServiceIntegrationTest              # uma classe
mvn spring-boot:run -DskipTests "-Dspring-boot.run.profiles=dev"
```
`spring-boot:run` contra um Postgres limpo exige aplicar antes
`src/test/resources/testcontainers/supabase-stubs.sql` (schemas `auth`/`storage` e roles do Supabase);
ver "Como rodar localmente" no README. Segredos locais de dev ficam em
`application-dev-local.yml` (raiz, gitignorado, importado pelo profile `dev`).
Layout em casa (dois clones irmãos; pasta `servire` só no disco): README,
seção "Onde está o código (disco + GitHub)".

## Pacotes (`src/main/java/br/com/servire/api/`)
| Pacote | Conteúdo |
|---|---|
| `web/` | `GlobalExceptionHandler`, `ApiError`, hierarquia `ApiException` (`BadRequest`/`Conflict`/`Forbidden`/`ResourceNotFound`/`Unauthorized`/`TooManyRequests`), `RequestIdFilter` (MDC `requestId`), `ClientIp` (nunca lê `X-Forwarded-For` no código), `RestClientConfiguration`, `Formatos` (CPF, CNPJ alfanumérico, RG, CEP, UF, telefone, sexo) |
| `tenant/` | `Tenant` (global), `TenantEmail`/`TenantTelefone` (globais, sem `@TenantId`), `TenantContext`, `GET/PUT /tenant` |
| `security/` | `SecurityConfig`, `JwtAuthenticationFilter` (Kill Switch + perfil por requisição), `JwtService` (`access`, `tenant_selection`, `suporte_app`), handlers 401/403 |
| `auth/` | `Usuario`, `UsuarioTenant` (`perfil_id`; a `role` antiga só vale se o perfil for nulo), refresh token, token de senha (`RESET` 1h / `CONVITE` 7 dias), `EmailSender` (`LoggingEmailSender` / `ResendEmailSender`), `/auth/**` |
| `pessoa/` | cadastro pessoa-primeiro: `Pessoa` (`e_voluntario`/`e_responsavel`, podem coexistir), e-mails/telefones 1:N, `PessoaRelacao` (é/de, opcional), `/pessoas/**` |
| `voluntario/` | perfil 1:1 `@MapsId` com `Pessoa` (escala, foto, ativo, disponibilidade), `/voluntarios/**` — identidade não mora mais aqui |
| `escala/` | `Escala` → `EscalaEvento` → `EscalaVaga`, presença, picker de candidatos, `/escalas/**` |
| `inscricao/` | inscrição pública (`/public/{slug}/inscricoes`, rate limit + Turnstile) e fila `/inscricoes/**`; aprovar materializa `Pessoa` e reusa RESPONSAVEL por e-mail principal |
| `storage/` | `SupabaseStorageService` (REST via `RestClient`, bucket privado `voluntarios-fotos`) |
| `audit/` | `AuditLog`, `AuditLogService.registrar(...)`, `GET /audit-log` (ADMIN) |
| `acesso/` | perfis da paróquia, usuários por convite, `GET/PUT /me` (devolve `permissoes`: códigos efetivos do catálogo, para o menu). `CatalogoPermissao` é a **única** lista de permissões (seção → módulo → ações); `PermissoesDaSessao` vira `PERM_<código>`; `ConcessaoDePermissao` impede conceder mais do que a sessão tem |
| `integracao/` | contrato v1 com a Central: `IntegracaoFiltro` (HMAC → `PERM_INTEGRACAO`), provisionamento idempotente, `direitos_locais`, `AcessoParoquia` (regra única de paróquia liberada, usada no login e no filtro), webhook, sync de 8h + alerta, código de suporte, `CatalogoDeRecursos` (`GET /integracao/v1/recursos`: códigos de limite/funcionalidade e se já são aplicados; aplicar um = trocar `aplicado` no mesmo commit) |
| `diocese/` | diocese **só como agrupamento informativo** da paróquia (V037, sem cota). Global, sem `@TenantId`. A paróquia escolhe ou digita o nome no `PUT /tenant` (`DioceseService.resolver`: mesmo nome sem diferenciar maiúsculas = mesma diocese); `GET /dioceses` sugere as já usadas. Diocese que contrate em bloco vira cliente na Central, não regra do Servire |

Migrations: `src/main/resources/db/migration/V001..V037`. **V001–V015 são o baseline, nunca editar.**
Mudança de schema = nova migration `V0NN__descricao.sql`. V030: `pessoa` + contatos 1:N + `pessoa_relacao` + `tenant_email`/`tenant_telefone` (globais) + espelho da inscrição; drop de `responsaveis`. V031: papéis concomitantes (`e_voluntario`/`e_responsavel`); responsável deixa de ser obrigatório. V032: drop do enum órfão `pessoa_papel`.
**Produção está na V032** desde 24/09/2026 (banco recriado do zero por script manual, com `flyway_schema_history`
gerado com os checksums reais — ver README, "Produção (banco)"). **Nunca editar migration já aplicada** (checksum
diferente = a API não sobe). V033/V034 (MESC, mandato, diocese) já estão no `main`. V035 (perfil e convite) e
V036 (integração v1) são aditivas: as tabelas de billing continuam no banco até a Central existir para recebê-las.
V037 tira a cota da diocese (gatilho, `tenant.voluntarios_ativos` e `diocese.cota_voluntarios`) e troca o único de `nome` por `lower(nome)`.

## Multi-tenancy (P0 — regras que não podem ser quebradas)
- Entidades de domínio têm `@TenantId UUID tenantId` (Hibernate filtra e preenche sozinho).
  `Tenant` e `Usuario` são globais. FKs compostas `(tenant_id, id)` no banco (V021).
- Tenant vem **só do JWT** (`JwtAuthenticationFilter` → `TenantContext`). Nunca de body, path ou header.
  Exceção deliberada: JWT `purpose=suporte_app` (código de uso único da Central) seta o
  `TenantContext` da paróquia **sem** `usuario_tenant` e aceita paróquia bloqueada. A entrada
  e as ações seguintes vão para `audit_log` com nome, e-mail e motivo do operador.
  `perfil` é global (FK `tenant_id`, sem `@TenantId`) porque o filtro JWT lê fora de transação.
  Sem linha em `direitos_locais`, o Kill Switch continua no status do tenant. Com a linha,
  libera se `acesso_liberado` e a confirmação tiver no máximo a tolerância (72h).
  Idem `tenant_email`/`tenant_telefone` (V030): globais, sem `@TenantId`.
- Sem `TenantContext`, o resolver devolve o sentinela `SEM_TENANT` (UUID zero): leituras vêm vazias e
  escritas falham por FK. Não "consertar" isso lançando exceção no resolver (quebra o bootstrap).
- O Hibernate fixa o tenant **quando a sessão abre** (entrada do `@Transactional`). Código que define
  `TenantContext` por conta própria (só `InscricaoService.criarPublica`) precisa abrir a transação
  **depois** disso, com `TransactionTemplate`.
- **Proibido SQL nativo/JDBC direto em tabela tenant-aware** (Native Query Gate, seção 81): use JPQL,
  derived queries ou Criteria/`Specification`.

## Convenções de código
- Injeção por construtor; `EntityManager` via `@PersistenceContext` quando precisa de `flush()`.
- DTOs são `record`s em `<modulo>/dto/`, com `static de(Entidade)` na resposta e Bean Validation no request.
- Controller: `@PreAuthorize("hasAuthority('PERM_<CÓDIGO>')")` em **todo** método, com o código do
  `CatalogoPermissao`: o **módulo** para leitura (`PERM_ESCALA`) e a **ação** para escrita
  (`PERM_ESCALA_EXCLUIR`, `PERM_VAGA_PRESENCA`). Nunca uma permissão "de escrita geral": a matriz do perfil
  vale por ação. Ação nova = entra no catálogo + no `@PreAuthorize`. `/integracao/**` exige `PERM_INTEGRACAO`
  (só o HMAC concede). Toda rota não pública é autenticada.
- Service: `@Transactional` / `@Transactional(readOnly = true)`; erro de negócio = subclasse de `ApiException`
  (vira JSON `ApiError` com `requestId`). Nunca vazar detalhes internos num 500.
- CPF, CNPJ, RG, CEP, UF, telefone e sexo passam por `Formatos` no service antes de gravar (valida e grava sempre no
  mesmo formato, ex.: `529.982.247-25`, `(45) 99999-8888`); e-mail no DTO com `@Email(regexp = Formatos.EMAIL)`.
  O front tem as mesmas regras em `shared/utils/formatos.ts`; mudou uma, muda a outra.
- Mudança relevante chama `auditLogService.registrar("ACAO", "ENTIDADE", id, camposAlterados)` de forma explícita (sem AOP).
- Javadocs longos que explicam o **porquê** (com data e seção do plano) são o estilo da casa. Mantenha-os ao mexer no código.
- Segredos nunca versionados: prod lê de env var **sem valor padrão** (`JWT_SECRET`, `DB_URL`, `SUPABASE_*`,
  `TURNSTILE_SECRET_KEY`, `RESEND_API_KEY`). Em `application-dev.yml`, **não** colocar placeholder vazio
  `${X:}` para chave que vem de `application-dev-local.yml`: o vazio sobrescreve o arquivo importado.
  Env var nova do profile `prod` entra também no `.env.example` (é a lista usada no deploy).

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
  `server.forward-headers-strategy: native` + `internal-proxies` (Nginx/Caddy no
  mesmo VPS). O proxy tem que **sobrescrever** o header (`$remote_addr`), não
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
- Tabela nova em `public` (que não seja tenant-aware com policy): `ENABLE ROW LEVEL SECURITY` sem
  policy, senão a Data API do Supabase (chave anon) lê/grava. A API Java é dona das tabelas e não é afetada.
- Entidade com **chave preenchida à mão** (`integracao_operacao`, `integracao_nonce`): o `save` do Spring
  Data faz `merge` (UPDATE) e a PK nunca esbarra — nonce repetido passava. Use `Persistable` com `isNew`
  (`IntegracaoOperacao`) ou `INSERT ... ON CONFLICT DO NOTHING` contando linhas (`IntegracaoNonceRepository`).
- `/integracao/**` **não** é `permitAll`. O filtro decide pelo caminho decodificado (`servletPath`) e assina o
  cru; o controller exige `PERM_INTEGRACAO`, então um caminho que escape do filtro (`/%69ntegracao/...`) dá 401.
- Bloqueio por atraso é decisão da Central; o app obedece `acesso_liberado` da cópia local. Operador do SaaS
  não existe mais no app (coluna `usuario.operador_saas` fica no banco até o corte, sem mapeamento).

## Testes (`src/test/java/...`)
- Integração estende `AbstractIntegrationTest`: um Postgres 17 singleton (sem `@Container`, de propósito;
  mesma versão major da produção — ao atualizar o Supabase, atualizar a imagem junto),
  stub do Supabase aplicado uma vez, profile `test` (e-mail fixo em `log`, storage/turnstile vazios).
- Cada teste cria seu **próprio tenant descartável** com `codigo`/`slug` aleatórios e faz
  `TenantContext.set(...)` no `@BeforeEach` / `clear()` no `@AfterEach`. Não há rollback automático.
- Testes de serviço/repositório **não** usam `@Transactional` na classe (fixaria o tenant errado).
- Autorização HTTP: `MethodSecurityIntegrationTest` (MockMvc + `@MockitoBean` nos services). Clientes HTTP
  externos: `MockRestServiceServer`, sem contexto Spring.
- Ao adicionar migration: atualizar total e descrição da última em `FlywayMigrationIntegrationTest`.
- Integração (`IntegracaoHttpIntegrationTest`): requisições assinadas de verdade com a chave `teste-central`
  do `application-test.yml` (mesmo segredo dos vetores do contrato). Regras de perfil/usuário e a trava de
  concessão: `AcessoIntegrationTest` (monta a sessão à mão; sem autenticação a trava não se aplica).
- Nomes de teste em português, descritivos (`atualizarComVersaoDivergenteLancaConflictException`).
- Voluntário em teste: `Pessoas.persistirVoluntario(pessoaRepository, "Nome")` — não existe mais
  `new Voluntario("Nome")`. Papéis podem coexistir; responsável é opcional (adulto/ministro).
