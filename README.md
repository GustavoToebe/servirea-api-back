# Servire API — Fases 2, 3, 4 e 5 (fundação + modelo SaaS lógico + multi-tenancy + autenticação própria)

Este projeto cobre a **FASE 2** (fundação Spring Boot, seção 102/125), a
**FASE 3** (modelo SaaS lógico, seção 103/126), a **FASE 4**
(`TenantContext`/`@TenantId`, seção 104/126) e a **FASE 5** (autenticação
própria, seção 105/32-36) do `plano_mestre_servire_v2_mvp_baixo_custo.md`,
antes de migrar qualquer módulo de negócio completo (voluntário, escala,
inscrição — isso é Fase 6 em diante).

## ✅ Fases 2, 3 e 4 com build verificado de verdade (21/09/2026)

`mvn clean verify` rodado pelo usuário no ambiente real (Windows, Docker
Desktop): `BUILD SUCCESS`, **13 testes, 0 falhas, 0 erros**. Repositório
sincronizado em `https://github.com/GustavoToebe/servire-api`. Ver as
seções abaixo para o detalhamento de cada fase e os bugs reais encontrados
e corrigidos ao longo do caminho — nenhum ficou sem confirmação final.

## ⏳ Fase 5 implementada, ainda NÃO confirmada por build real (21/09/2026)

Todo o código da Fase 5 (autenticação própria — ver seção dedicada logo
abaixo) foi escrito e compõe este repositório. O usuário já rodou
`mvn clean verify` uma primeira vez e encontrou um bug real de verdade
(**bug real #4**, corrigido — ver detalhamento na seção da Fase 5
abaixo), mas **ainda não há uma execução completa com `BUILD SUCCESS`
confirmando que os testes anteriores continuam passando** com
`spring-boot-starter-security` agora no classpath. Este aviso só sai
daqui quando isso acontecer (mesma prática usada nos bugs reais #1/#2/#3).

## Decisão de versão: Spring Boot 4.1.1 (não 3.x)

O plano mestre original (seção 119) documentava "Spring Boot 3.x". Ao
começar esta fase (21/09/2026), pesquisei e confirmei que a linha 3.5 (a
última do Spring Boot 3) chegou ao fim do suporte open-source em
30/06/2026 — hoje a versão suportada é a linha 4.1.x (atual: 4.1.1,
agosto/2026). Perguntei ao usuário e ele confirmou: começar já em Spring
Boot 4.1.x. Isso implica **Jakarta EE 11** e **Spring Framework 7** — na
prática, baixo impacto para este projeto, que já nasce usando `jakarta.*`
(convenção que já valia desde o Boot 3). O plano mestre foi atualizado
(seção 119) para refletir essa mudança.

## O que está incluído nesta fase

| Item (seção 102) | Onde |
|---|---|
| `/health` | Spring Boot Actuator (`/actuator/health`), `show-details` fechado por padrão (sem Spring Security ainda — chega na Fase 5) |
| Exception handling | `web/GlobalExceptionHandler.java` + hierarquia `ApiException`/`ResourceNotFoundException`/`ConflictException`/`BadRequestException`/`ForbiddenException` — nunca vaza detalhe interno (stack trace, mensagem de driver JDBC) numa resposta 500 |
| Logging estruturado | `logback-spring.xml` — JSON em produção/test, texto legível em dev; campos `tenantId`/`userId` da seção 65 ficam ausentes de propósito até existirem (Fases 4/5) |
| requestId | `web/RequestIdFilter.java` — gera/propaga `X-Request-Id`, injeta no MDC (aparece em todo log da requisição e no corpo de erro) |
| Config | `application.yml` + profiles `dev`/`test`/`prod` |
| JPA | `spring-boot-starter-data-jpa`, `ddl-auto: validate` (o Flyway é dono do schema, não o Hibernate) |
| Flyway | `src/main/resources/db/migration/V001-V015` — as mesmas migrations do baseline da Fase 0/1 (`servire-database/migrations`), copiadas para o local padrão que o Flyway do Spring Boot já escaneia sozinho |
| Testcontainers | `AbstractIntegrationTest` — sobe um Postgres 16 real, aplica o stub de `auth`/`storage`/roles do Supabase, e roda as migrations de verdade via Spring Boot |

## ✅ Build verificado (21/09/2026), Fase 2: `mvn clean verify` passou, 11/11 testes

O ambiente onde este código foi escrito originalmente tinha acesso de rede
bloqueado para o Maven Central, então não deu pra rodar o build lá. O
usuário rodou de verdade no próprio ambiente (Windows, Docker Desktop) e
apareceram dois erros reais — ambos corrigidos e já confirmados com
`BUILD SUCCESS` depois:

1. **`org.testcontainers:junit-jupiter`/`postgresql` sem versão gerenciada.**
   Causa: o Testcontainers 2.x (gerenciado pelo BOM que o Spring Boot 4.1.x
   importa) renomeou os artefatos Maven com o prefixo `testcontainers-`
   (`postgresql` → `testcontainers-postgresql`, `junit-jupiter` →
   `testcontainers-junit-jupiter`) — os nomes antigos não têm versão
   gerenciada nesse BOM. Pacotes Java não mudaram
   (`org.testcontainers.containers.PostgreSQLContainer`,
   `org.testcontainers.junit.jupiter.*`).

2. **Flyway nunca rodava — sem log, sem erro, silenciosamente.** As 15
   migrations nunca criavam as tabelas nos testes (`flyway_schema_history`
   nem existia), mas a aplicação subia normal, porque ainda não há nenhuma
   `@Entity` JPA pra validar contra o schema. Causa: no Spring Boot 4, a
   auto-configuration do Flyway foi extraída do `spring-boot-autoconfigure`
   monolítico para um módulo próprio (`spring-boot-flyway`), só ativado
   pelo starter `spring-boot-starter-flyway`. Ter só `flyway-core` no
   classpath (jeito do Boot 3, usado na primeira versão deste projeto)
   compila normalmente, mas o Flyway nunca é acionado — confirmado no guia
   oficial de migração do Spring Boot 4.0. Trocado `flyway-core` por
   `spring-boot-starter-flyway` no `pom.xml`.

Depois dessas duas correções: `mvn clean verify` → `BUILD SUCCESS`, 11
testes rodados (5 de `FlywayMigrationIntegrationTest`, incluindo as
verificações de RLS e grants das RPCs; 1 de contexto Spring; 3 do
exception handler; 2 do `RequestIdFilter`), 0 falhas, 0 erros — as 15
migrations sendo aplicadas de verdade num Postgres 16 via Testcontainers +
Docker Desktop. O `Dockerfile` continua não testado (build de imagem
Docker em si, diferente de Testcontainers) — validar quando for a hora do
deploy.

**Atualização (21/09/2026): a observação acima estava errada — não era
"não bloqueante".** Ver bug real #3 mais abaixo: isso derrubou de verdade
dois testes de `TenantIsolationIntegrationTest` assim que o número de
classes de teste com container cresceu.

## ✅ Fases 3/4 implementadas (21/09/2026): modelo SaaS lógico + isolamento multi-tenant

Depois do build da Fase 2 confirmado (seção acima), foram implementadas as
Fases 3 e 4 do plano mestre (seção 103/104/126):

**Fase 3 — modelo SaaS lógico:**

- Migrations `V016`-`V019`: tabelas `tenant`, `usuario`, `usuario_tenant`,
  `refresh_token` (o "Master lógico", seção 25).
- `V020`: seed de um tenant inicial com UUID fixo e conhecido
  (`00000000-0000-0000-0000-000000000001`) — `codigo`/`slug`/`nome`
  marcados como **PLACEHOLDER** no comentário da própria migration até o
  nome real da paróquia ser informado.
- `V021`: adiciona `tenant_id NOT NULL` às 7 tabelas de domínio
  (`voluntarios`, `responsaveis`, `escalas`, `escala_eventos`,
  `escala_vagas`, `inscricoes`, `inscricao_responsaveis`), faz o backfill
  de tudo que já existia para o tenant seed, e troca as FKs simples entre
  elas por FKs compostas tenant-aware (`FOREIGN KEY (tenant_id, x) REFERENCES
  outra_tabela (tenant_id, id)`, seção 23) — o banco passa a impedir
  referência cross-tenant por conta própria, não só o filtro do Hibernate.
- Entidades JPA criadas **só para `tenant` e `usuario`**
  (`tenant/Tenant.java`+`TenantRepository.java`,
  `auth/Usuario.java`+`UsuarioRepository.java`). **`usuario_tenant` e
  `refresh_token` ficaram só no SQL por enquanto** — decisão deliberada:
  as duas têm chave primária composta via `@MapsId`, que exige que
  `Usuario`/`Tenant` já estejam persistidos (com ID gerado) antes de
  montar o ID do filho — uma ordem de operações fácil de errar sem os
  testes de login/JWT reais da Fase 5 para exercitá-la de verdade. Melhor
  adiar do que publicar código de chave composta não testado.

**Fase 4 — TenantContext e `@TenantId`** (pacote `tenant/`):

- `TenantContext` — `ThreadLocal<UUID>`, `set`/`get`/`clear`; regra da
  seção 20 (nunca deixar vazar entre requisições) aplicada via
  `try/finally` em todo chamador.
- `ServireCurrentTenantIdentifierResolver` — implementa
  `CurrentTenantIdentifierResolver<UUID>` do Hibernate; retorna um UUID
  sentinela reservado (`SEM_TENANT`, o UUID nulo) quando não há
  `TenantContext` definido, em vez de lançar exceção — ver o bug real
  encontrado e corrigido logo abaixo.
- `TenantConfiguration` — liga o resolver ao Hibernate via um bean
  `HibernatePropertiesCustomizer` e `MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER`.
  **Nota de import:** no Spring Boot 4.1.1, `HibernatePropertiesCustomizer`
  mudou de pacote em relação ao Boot 3.x — agora é
  `org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer`
  (a auto-configuração do Hibernate também foi modularizada, no mesmo
  espírito da mudança do Flyway descrita acima).
- `DevFixedTenantFilter` — **andaime temporário, remover na Fase 5**:
  popula `TenantContext` com o tenant seed em toda requisição, já que
  ainda não existe JWT (seção 104: "inicialmente tenant pode ser fixo em
  desenvolvimento").
- Primeira entidade tenant-aware: `voluntario/Voluntario.java`, mapeando
  só `id`/`tenantId` (`@TenantId`)/`nomeCompleto`/`ativo` da tabela
  `voluntarios` — deliberadamente mínima, só o suficiente para provar o
  mecanismo de isolamento. Entidade completa (tipo, endereço,
  `funcoes_habilitadas`, etc.) fica para a Fase 6.
- Teste crítico de isolamento (seção 78/79/80, P0):
  `tenant/TenantIsolationIntegrationTest.java` — cria um segundo tenant,
  salva um voluntário em cada um, e confirma que o tenant A não vê o
  voluntário do tenant B nem por `findAll()` nem buscando pelo ID de B
  diretamente (o cenário mais crítico da matriz da seção 79). Também
  confirma que salvar sem `TenantContext` definido nunca grava um
  registro com tenant errado (ver bug/correção abaixo para o motivo do
  mecanismo exato).

**⚠️ Risco residual não verificado ao vivo:** `Tenant.status` mapeia o
enum nativo `tenant_status` do Postgres via `@Enumerated(EnumType.STRING)`
+ `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` — padrão confirmado como existente
desde o Hibernate 6.5 (`PostgreSQLEnumJdbcType`), mas **não foi possível
reconfirmar contra a versão exata deste projeto (Hibernate ORM
7.4.5.Final)** por ter batido um limite de sessão de pesquisa no momento
da implementação. Se o próximo `mvn clean verify` acusar erro de
schema/tipo na coluna `status` de `tenant`, este é o primeiro lugar a
checar (comentário equivalente deixado direto no código de `Tenant.java`).

### 🐛 Bug real encontrado e corrigido (21/09/2026): resolver derrubava a inicialização inteira

O usuário rodou `mvn clean verify` de verdade sobre este código e **todos**
os testes falharam — inclusive `ServireApiApplicationTests`, que não tem
nada a ver com tenant. Isso já era o sinal de que a causa era uma só,
acontecendo na inicialização do Spring, não em cada teste individualmente.

O stack trace completo (retirado de `target/surefire-reports/`, já que o
Maven só imprime o erro raiz na primeira falha e resume as seguintes como
"threshold exceeded") mostrou:

```text
BeanCreationException: Error creating bean 'tenantRepository' ...
Caused by: QueryCreationException: Cannot create query for method [TenantRepository.findBySlug] ...
Caused by: IllegalStateException: TenantContext não definido para a thread atual
    at ServireCurrentTenantIdentifierResolver.resolveCurrentTenantIdentifier
    at SessionFactoryImpl.resolveTenantIdentifier
```

**Causa:** o Spring Data JPA sonda cada repositório por named queries já na
criação do bean (`NamedQuery.hasNamedQuery`, chamado durante
`preInstantiateSingletons` — ou seja, na inicialização do contexto, antes
de qualquer requisição HTTP existir). Essa sondagem cria um
`EntityManager`, e sob um `SessionFactory` multi-tenant isso **sempre**
resolve o tenant atual — mesmo para entidades globais sem `@TenantId`
(`Tenant`, `Usuario`) e mesmo sem nenhuma query de verdade rodando. A
primeira versão do resolver lançava `IllegalStateException` quando não
havia `TenantContext`, pensando em falhar alto e cedo num bug de
aplicação (seção 78/79/80) — só que essa checagem também disparava neste
momento de bootstrap do Spring, onde não existe (nem precisa existir)
requisição nenhuma, derrubando a aplicação inteira antes de qualquer
teste rodar.

**Correção:** o resolver não lança mais exceção. Quando não há
`TenantContext` definido, ele retorna um UUID sentinela reservado
(`ServireCurrentTenantIdentifierResolver.SEM_TENANT`, o UUID nulo
`00000000-0000-0000-0000-000000000000`), que nunca existe na tabela
`tenant`. Isso resolve o bootstrap sem quebrar nada, e ainda mantém a
proteção P0: qualquer INSERT/UPDATE real numa tabela tenant-aware sem
`TenantContext` definido continua falhando — agora por violação de
foreign key (`voluntarios_tenant_id_fkey` e equivalentes) em vez de uma
exceção lançada no resolver. Uma leitura (`findAll`) sem contexto
simplesmente filtra por um `tenant_id` que não existe e retorna vazio.
`TenantIsolationIntegrationTest.salvarSemTenantContextDefinidoNaoDeveGravarLinhaComTenantErrado`
foi atualizado para esperar `DataIntegrityViolationException` (violação de
FK), não mais `IllegalStateException`.

**✅ Confirmado pelo `mvn clean verify` seguinte do usuário:** o erro
genérico de "threshold exceeded" em todas as classes desapareceu — o
contexto Spring volta a subir normalmente. Esse build seguinte revelou,
porém, mais dois bugs reais e independentes (abaixo), então Fase 3/4 ainda
não estava com `BUILD SUCCESS` completo nesse momento.

### 🐛 Bug real #2 (21/09/2026): contagem de migrations desatualizada no teste

`FlywayMigrationIntegrationTest` ainda afirmava `total == 15` e
`sucesso == 15` (bagagem da Fase 2, antes de `V016`-`V021` existirem).
Com as 6 migrations novas da Fase 3/4, o total real passou a ser 21.
Falha determinística e esperada, sem nada de arquitetural — só o teste
não tinha acompanhado a nova contagem.

**Correção:** teste atualizado para esperar `21`/`21`, e a asserção do
`getLast()` (última migration aplicada) trocada de `"storage bucket"`
(V015, fim do baseline antigo) para `"add tenant id domain tables"`
(V021, a mais nova). **Confirmado pelo build seguinte do usuário:**
`FlywayMigrationIntegrationTest` passa limpo, 5/5.

### 🐛 Bug real #3 (21/09/2026): "singleton container" que na prática não era singleton

Depois dos bugs #1 e #2 corrigidos, sobrou uma falha real e reproduzível
(em dois builds seguidos) só em `TenantIsolationIntegrationTest`: as duas
tarefas falhavam com `CannotCreateTransactionException` encadeando até
`Connection refused`/timeout de 30s do HikariCP — parecendo, à primeira
vista, uma instabilidade do Docker Desktop/WSL2.

O log completo (não só o resumo do console, que o Maven trunca) mostrou o
mecanismo real: **três containers Postgres diferentes** foram criados, um
por classe de teste (`FlywayMigrationIntegrationTest` na porta 51516,
`ServireApiApplicationTests` na 51532, `TenantIsolationIntegrationTest` na
51535) — mesmo o campo `POSTGRES` em `AbstractIntegrationTest` sendo
`static final` e todas as classes rodando na mesma fork/JVM do Surefire
(sem `forkCount`/`reuseForks` customizado no `pom.xml`). Pior: o
`HikariPool` de `TenantIsolationIntegrationTest` tentava (e falhava)
conectar na porta **51516** — a primeira classe, já com o container
parado — em vez da sua própria, recém-criada, na 51535.

**Causa raiz (duas partes):**

1. `AbstractIntegrationTest` usava `@Testcontainers` + `@Container` no
   campo estático — o padrão mais comum em tutoriais, mas que **não** é a
   forma correta de compartilhar container entre classes. Essa extensão
   JUnit5 gerencia o ciclo de vida por CLASSE de teste: chama `start()` no
   `beforeAll` e **`stop()` no `afterAll`** — mesmo em campo `static`
   herdado. Cada classe nova, então, parava o container da anterior e
   subia um novo, com porta mapeada diferente.
2. Isso por si só custaria só tempo (subir Postgres 3x), não corretude —
   o que tornou bug real foi combinar com um comportamento documentado do
   Spring: o cache de `ApplicationContext` do `@SpringBootTest` **não**
   considera os valores de `@DynamicPropertySource` na chave do cache, só
   as anotações estáticas da classe. Como `TenantIsolationIntegrationTest`
   tem a mesma configuração "estática" de `FlywayMigrationIntegrationTest`
   (nenhuma anotação extra além do que já está em `AbstractIntegrationTest`),
   o Spring reaproveitou do cache o contexto/`DataSource` já construído
   pela primeira classe — apontando pra um container já morto — em vez de
   construir um novo apontando pro container recém-subido daquela classe.

**Correção:** seguir à risca o padrão oficial de "singleton container" do
Testcontainers, que deliberadamente NÃO usa `@Container`/`@Testcontainers`
— o container é iniciado uma única vez, manualmente, num bloco estático
(`static { POSTGRES.start(); }`), e nunca é parado explicitamente (a
limpeza ao final do processo continua por conta do Ryuk, como já era).
Sem `@Container`, a extensão JUnit5 nunca tenta parar esse container entre
classes — ele nasce uma vez e vive até o fim do `mvn`, tornando irrelevante
o comportamento de cache do Spring aqui (com ou sem cache, o container é
sempre o mesmo, na mesma porta).

**✅ Confirmado pelo `mvn clean verify` seguinte do usuário: `BUILD SUCCESS`,
13 testes, 0 falhas, 0 erros.** Conferido diretamente em
`target/surefire-reports/` no ambiente do usuário: `TenantIsolationIntegrationTest`
passou em 0,427s (antes travava por volta de 60s tentando conectar num
container já morto). Com isso, a Fase 3/4 está de fato encerrada com build
verificado de verdade — não só compilando, mas com os três bugs reais
encontrados nesta fase corrigidos e confirmados.

## Fase 5 — autenticação própria (seção 105/32-36 do plano mestre)

Login, JWT (access token + refresh token com rotation), seleção de
paróquia, logout, e "esqueci minha senha"/"redefinir senha" — tudo
seguindo o Kill Switch (seção 28) e sem depender mais do Supabase Auth do
front-end atual.

**Entidades JPA que faltavam desde a Fase 3** (deliberadamente adiadas —
ver seção acima): `UsuarioTenant` (`@EmbeddedId` + `@MapsId` duplo, chave
composta `usuario_id`+`tenant_id`) e `RefreshToken` (chave simples — na
Fase 3 achei, por engano, que também precisava de `@MapsId`; a migration
V019 sempre teve `id uuid PRIMARY KEY DEFAULT gen_random_uuid()` simples,
então adiar não era estritamente necessário, mas também não causou dano,
já que o fluxo que usa essa entidade só existe agora). Nova migration
`V022__table_password_reset_token.sql` para o fluxo de reset de senha
(total de migrations passa de 21 para 22).

**Endpoints (`AuthController`, todos sob `/auth`):**

- `POST /auth/login` — e-mail+senha. Se o usuário tiver só uma paróquia
  ativa, já devolve os tokens completos; se tiver mais de uma, devolve um
  token de seleção de tenant (5 min) e a lista de paróquias disponíveis
  (seção 30).
- `POST /auth/select-tenant` — troca o token de seleção pelos tokens
  completos da paróquia escolhida.
- `POST /auth/refresh` — rotaciona o refresh token (seção 36: o
  apresentado é revogado, um novo é emitido). Reuso de um token já
  revogado é tratado como possível roubo/cópia — revoga de uma vez TODAS
  as sessões daquele usuário.
- `POST /auth/logout` — revoga o refresh token da sessão atual.
- `POST /auth/forgot-password` — sempre responde 202, exista ou não o
  e-mail (nunca revela quais e-mails estão cadastrados). O envio de fato
  do e-mail está stubado em `LoggingEmailSender` (só loga em DEBUG) — não
  é produção-ready, é um placeholder explícito até decidir um provedor de
  e-mail de verdade com o usuário.
- `POST /auth/reset-password` — consome o token de reset (uso único) e
  troca a senha. Trocar senha é tratado como evento de segurança: revoga
  todos os refresh tokens do usuário, forçando login de novo em todos os
  dispositivos (mesma reação usada no reuso de refresh token detectado).

**Transporte do refresh token:** cookie `HttpOnly`+`Secure`+`SameSite=None`,
escopado a `/auth` (seção 92 — decisão tomada com o usuário ao iniciar
esta fase). Nunca aparece no corpo de nenhuma resposta JSON.

**`SecurityConfig`/`JwtAuthenticationFilter`** substituem por completo o
`DevFixedTenantFilter` da Fase 4 (removido nesta fase, como já estava
previsto na sua própria javadoc e na seção 104 do plano mestre):

- `JwtAuthenticationFilter` extrai o access token do header
  `Authorization: Bearer <token>`, valida assinatura/expiração/finalidade
  via `JwtService`, e então **revalida o Kill Switch direto no banco a
  cada requisição** (usuário ativo, vínculo `usuario_tenant` ATIVO, tenant
  ATIVO/TRIAL) — isso é deliberado: sem essa revalidação, bloquear um
  usuário ou uma paróquia só teria efeito depois que o access token
  expirasse (15 minutos). Se passar, popula `TenantContext` (igual o
  filtro antigo fazia) e o `SecurityContextHolder`.
- CSRF via `csrf.spa()` (método de conveniência do Spring Security 7.0
  pensado para SPA — confirmado contra a documentação oficial), isento
  em `/auth/login`, `/auth/select-tenant`, `/auth/forgot-password` e
  `/auth/reset-password` (não dependem de nenhuma credencial ambiente do
  navegador); `/auth/refresh` e `/auth/logout` continuam protegidos, por
  dependerem do cookie do refresh token.
- CORS restrito às origens de `CORS_ALLOWED_ORIGINS` (nunca `*`), com
  `allowCredentials(true)` (necessário para o cookie do refresh token
  atravessar requisições cross-site até a API).
- Sessão `STATELESS` — nenhuma sessão HTTP é criada; tudo é resolvido a
  cada requisição a partir do access token.
- Erros de autenticação/autorização (`RestAuthenticationEntryPoint`/
  `RestAccessDeniedHandler`) devolvem o mesmo formato `ApiError` usado no
  resto da API, em vez da página HTML padrão do Spring Security — esses
  dois pontos ficam fora do alcance do `GlobalExceptionHandler` porque
  rodam dentro da cadeia de filtros, antes de qualquer controller.

**Outras decisões desta fase:**

- Senha: `PasswordEncoderFactories.createDelegatingPasswordEncoder()`
  (BCrypt como algoritmo padrão) — confirmado como o padrão oficialmente
  recomendado pelo Spring Security, escolhido em vez de Argon2 para não
  precisar de uma dependência extra (Bouncy Castle) nesta fase.
- Refresh token e token de reset de senha: hash SHA-256 (não bcrypt/
  argon2) — motivo documentado na javadoc de `OpaqueTokenGenerator`: são
  segredos de alta entropia gerados pelo servidor (não senhas escolhidas
  por humanos), e precisam de busca indexada exata (`WHERE token_hash =
  ?`), que um hash salgado/não determinístico como bcrypt não permite.
- `JJWT` 0.13.0 (pesquisado e confirmado como a versão mais recente em
  21/09/2026) para emissão/validação do JWT, com dois `purpose` de token
  distintos (`access` vs `tenant_selection`) para nunca aceitar um token
  de seleção de paróquia como se fosse um token de acesso normal, ou
  vice-versa.

### 🐛 Bug real #4 (21/09/2026): `jackson-databind` sumia da compilação por mediação de dependências do Maven

Primeiro `mvn clean verify` real da Fase 5 falhou na compilação, com
`package com.fasterxml.jackson.databind does not exist` em
`RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` — as duas
classes que montam a resposta 401/403 no formato `ApiError` manualmente
(porque rodam dentro da cadeia de filtros do Spring Security, fora do
alcance do `GlobalExceptionHandler`), usando `ObjectMapper` diretamente.

**Causa raiz:** `jackson-databind` nunca tinha sido uma dependência
DIRETA do projeto — chegava só transitivamente, bem fundo na árvore, via
`spring-boot-starter-web` → `spring-boot-starter-json`. Nenhuma classe
antes desta fase importava `ObjectMapper` no código-fonte (o
`GlobalExceptionHandler` já existente desde a Fase 2 só devolve
`ResponseEntity<ApiError>` e deixa o Spring MVC serializar via seus
conversores de mensagem, sem precisar de import direto). Ao adicionar
`spring-security-test` (escopo `test`) nesta mesma fase, o Maven passou
a enxergar `jackson-databind` por um caminho bem mais RASO da árvore de
dependências (via `spring-security-test`) do que o caminho antigo via
`spring-boot-starter-web` — e a regra de mediação "nearest definition"
do Maven usa a declaração mais próxima na árvore, mesmo que o escopo
dela (`test`) seja mais restrito que o da declaração mais distante
(`compile`). Resultado: `jackson-databind` ficou disponível só no
classpath de teste, sumindo do classpath de compilação principal — um
efeito colateral silencioso e nada óbvio de ter adicionado uma
dependência de teste.

**Correção:** declarar `com.fasterxml.jackson.core:jackson-databind`
como dependência DIRETA do projeto, sem escopo (= `compile`), no
`pom.xml`. Por ser uma declaração direta (profundidade 1), essa
dependência agora vence qualquer mediação por profundidade contra
caminhos transitivos mais fundos, independentemente de quais outras
dependências também a trazem. Sem versão própria: gerenciada pelo BOM
do Spring Boot 4.1.1, igual às demais dependências deste projeto sem
`<version>` explícita.

**⏳ Ainda não confirmado por um `mvn clean verify` completo** — o
usuário aplicou a correção e vai rodar o build de novo; esta ressalva
sai daqui quando o `BUILD SUCCESS` chegar.

## Como rodar localmente

Requer um PostgreSQL acessível (local, Docker, ou outro) para o profile
`dev` — nunca aponte o profile `dev` para o Supabase de produção.

```bash
cp .env.example .env
# edite .env com a URL do seu Postgres de desenvolvimento
export $(cat .env | xargs)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Como rodar os testes

```bash
mvn test
```

Os testes de integração (`AbstractIntegrationTest` e suas subclasses)
precisam de Docker disponível (Testcontainers sobe um Postgres real). Os
testes puramente unitários (`RequestIdFilterTest`, `GlobalExceptionHandlerTest`)
não precisam.

## Estrutura

```text
src/main/java/br/com/servire/api/
  ServireApiApplication.java
  web/
    RequestIdFilter.java
    GlobalExceptionHandler.java
    ApiError.java
    ApiException.java (+ subclasses: ResourceNotFoundException, ConflictException, BadRequestException, ForbiddenException, UnauthorizedException)
  tenant/
    Tenant.java, TenantRepository.java
    TenantContext.java
    ServireCurrentTenantIdentifierResolver.java
    TenantConfiguration.java
  security/
    SecurityConfig.java, SecurityProperties.java
    JwtService.java, JwtAuthenticationFilter.java, AuthenticatedUser.java
    OpaqueTokenGenerator.java
    RestAuthenticationEntryPoint.java, RestAccessDeniedHandler.java
  auth/
    Usuario.java, UsuarioRepository.java
    UsuarioTenant.java, UsuarioTenantId.java, UsuarioTenantRepository.java
    RefreshToken.java, RefreshTokenRepository.java, RefreshTokenService.java
    PasswordResetToken.java, PasswordResetTokenRepository.java, PasswordResetTokenService.java
    EmailSender.java, LoggingEmailSender.java   <- stub, ver seção Fase 5 acima
    AuthService.java, AuthController.java
    dto/   <- LoginRequest, LoginResponse, SelectTenantRequest, RefreshRequest, AccessTokenResponse, ForgotPasswordRequest, ResetPasswordRequest, TenantResumo
  voluntario/
    Voluntario.java, VoluntarioRepository.java   <- mínima, só para provar isolamento (Fase 6 faz a completa)
src/main/resources/
  application.yml, application-dev.yml, application-prod.yml
  logback-spring.xml
  db/migration/
    V001-V015.sql   <- baseline da Fase 0/1 (nunca editar)
    V016-V019.sql   <- tenant, usuario, usuario_tenant, refresh_token (Fase 3)
    V020.sql        <- seed do tenant inicial (placeholder de nome)
    V021.sql        <- tenant_id + FKs compostas nas 7 tabelas de domínio (Fase 3/4)
    V022.sql        <- password_reset_token (Fase 5)
src/test/java/br/com/servire/api/
  AbstractIntegrationTest.java  <- Testcontainers + stub Supabase
  ServireApiApplicationTests.java
  migration/FlywayMigrationIntegrationTest.java  <- versão JUnit das checagens de servire-database/local-dev/compare-schema.sql
  web/RequestIdFilterTest.java, GlobalExceptionHandlerTest.java
  tenant/TenantIsolationIntegrationTest.java  <- teste crítico P0 de isolamento (seção 78/79/80)
```

`DevFixedTenantFilter` (andaime temporário da Fase 4) foi removido nesta
fase, substituído por `security/JwtAuthenticationFilter.java` (ver seção
Fase 5 acima). Ainda não existem pacotes `config/`, `backoffice/`,
`inscricao/`, `escala/`, `arquivo/`, `billing/`, `auditoria/` da
estrutura-alvo completa (seção 16 do plano mestre) — eles entram
progressivamente a partir da Fase 6.

## Próximos passos

1. **Confirmar a Fase 5 com um `mvn clean verify` real** (ver aviso ⏳
   acima) — ainda falta escrever os testes de integração do fluxo de
   autenticação completo (login single-tenant/multi-tenant, seleção de
   paróquia, refresh com rotation, detecção de reuso, logout, Kill Switch
   em cada um desses pontos, forgot/reset password).
2. Roles/permissões de verdade aplicadas a endpoints de negócio (seção
   31) — hoje `authorizeHttpRequests` só distingue autenticado/não
   autenticado, sem checar a role do vínculo `usuario_tenant`.
3. Decidir um provedor de e-mail real para substituir `LoggingEmailSender`.
4. Só depois disso migrar os módulos de negócio completos (voluntário,
   responsável, inscrição, escala) do Angular/Supabase para cá (Fases
   6-9).
