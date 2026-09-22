# Servire API — Fases 2 a 10 (fundação + modelo SaaS lógico + multi-tenancy + autenticação própria + voluntários + storage + inscrições + escalas + multi-tenant real)

Este projeto cobre a **FASE 2** (fundação Spring Boot, seção 102/125), a
**FASE 3** (modelo SaaS lógico, seção 103/126), a **FASE 4**
(`TenantContext`/`@TenantId`, seção 104/126), a **FASE 5** (autenticação
própria, seção 105/32-36), a **FASE 6** (voluntários/responsáveis, seção
37/38/106), a **FASE 7** (storage de fotos, seção 107), a **FASE 8**
(inscrições públicas, seção 44/108), a **FASE 9** (escalas, seção
46/47/109) e a **FASE 10** (multi-tenant real, seção 110) do
`plano_mestre_servire_v2_mvp_baixo_custo.md`.

> ⏳ **Fase 10 (multi-tenant real) + todo o débito de testes automatizados
> pendente (Fases 5-9), feitos juntos numa única rodada em 22/09/2026, por
> instrução explícita do usuário — AINDA SEM confirmação de um
> `mvn clean verify` real.** Nenhum `mvn` local está disponível neste
> ambiente de pesquisa (Maven Central bloqueado); todo o código novo abaixo
> foi revisado manualmente, linha por linha, e teve as contagens de
> argumento de construtor/record checadas por um script auxiliar — mas
> **nada aqui deve ser tratado como "pronto" até o próximo build real do
> usuário confirmar.**
>
> **O que entrou nesta rodada:**
> - **Fase 10** (seção 110): suíte de isolamento multi-tenant ampliada em
>   `TenantIsolationIntegrationTest` cobrindo `Responsavel` (cascata de 1
>   nível), `Escala`→`EscalaEvento`→`EscalaVaga` (cascata de 3 níveis) e
>   `Inscricao`+`InscricaoResponsavel` — além do `Voluntario` que já existia
>   desde a Fase 4. Os itens "criar segundo tenant de teste"/"executar
>   suíte P0" da seção 110 ficam cobertos por esses testes; "ajustar
>   índices" já estava satisfeito pela V021 (nenhuma migration nova
>   necessária — ver seção própria da Fase 10 abaixo); "validar inscrição
>   pública por slug" fica coberto indiretamente pelo teste de isolamento
>   de `Inscricao` (cada tenant só é alcançável pelo próprio slug).
> - **Débito de testes pago** (Fases 5-9, antes conscientemente adiado em
>   favor de velocidade): `AuthServiceIntegrationTest` (login, seleção de
>   tenant, refresh/rotação/reuso, esqueci/redefinir senha — Fase 5),
>   `VoluntarioServiceIntegrationTest` (regras de responsável, "apaga tudo
>   e reinsere", foto — Fase 6), `SupabaseStorageServiceTest`/
>   `TurnstileServiceTest`/`InscricaoRateLimiterTest`/
>   `InscricaoServiceIntegrationTest` (Fases 7/8), `EscalaServiceIntegrationTest`
>   (transições de estado, controle otimista, "apaga tudo e reinsere" de
>   eventos — Fase 9), e um teste novo em `GlobalExceptionHandlerTest` para
>   o handler de `ObjectOptimisticLockingFailureException` (Fase 9).
> - **Bug real #7** (ver seção da Fase 7 abaixo): encontrado escrevendo
>   `SupabaseStorageServiceTest`, não por build — as três chamadas HTTP de
>   `SupabaseStorageService` codificavam a barra (`/`) do caminho do
>   arquivo como `%2F`, o que teria quebrado toda chamada real ao Supabase
>   Storage. Corrigido; **também ainda sem confirmação de build real.**
>
> ✅ **Fases 7, 8 e 9 com `BUILD SUCCESS` confirmado em 22/09/2026** — por
> instrução explícita do usuário, as três fases foram feitas juntas, numa
> única rodada, para só então pedir um build consolidado (ver seção
> própria de cada fase abaixo para detalhes e decisões).
>
> A primeira rodada desse build real (09:14-09:16) voltou `BUILD FAILURE`
> (13 testes, 0 falhas, **8 erros**) — **Bug real #6**: faltava o bean
> `RestClient.Builder`, usado por `SupabaseStorageService` (Fase 7) e
> `TurnstileService` (Fase 8). Corrigido com a nova classe
> `RestClientConfiguration` (detalhes na seção da Fase 7 abaixo). **Uma
> segunda rodada (09:24) confirmou o `BUILD SUCCESS`: 13 testes, 0
> falhas, 0 erros** — Bug real #6 está corrigido de verdade.
>
> ⚠️ **Isso confirma que o contexto Spring sobe e os 13 testes existentes
> continuam passando — não confirma o comportamento funcional das Fases
> 7/8/9 em si**, já que nenhum teste automatizado novo cobre o storage, as
> inscrições públicas ou as escalas (débito técnico assumido, seção
> "Próximos passos"). Os riscos residuais de cada fase (formato real da
> API do Supabase Storage, resposta do Cloudflare Turnstile,
> confiabilidade de `X-Forwarded-For` em produção, controle otimista das
> escalas sob concorrência real) continuam **não verificados** — ver as
> seções de cada fase abaixo.

## ✅ Fases 2, 3, 4 e 5 com build verificado de verdade (21/09/2026)

`mvn clean verify` rodado pelo usuário no ambiente real (Windows, Docker
Desktop): `BUILD SUCCESS`, **13 testes, 0 falhas, 0 erros** (confirmado às
23:37 de 21/09/2026). Repositório sincronizado em
`https://github.com/GustavoToebe/servire-api`. Ver as seções abaixo para
o detalhamento de cada fase e os bugs reais encontrados e corrigidos ao
longo do caminho — nenhum ficou sem confirmação final.

Histórico das tentativas de build da Fase 5 até chegar no `BUILD SUCCESS`:
1ª tentativa: falhou na **compilação** (bug real #4 — `jackson-databind`
sumindo do classpath por mediação de dependências do Maven).
2ª tentativa: compilação passou (48 arquivos-fonte), mas o **contexto do
Spring falhou ao subir** em todo teste que carrega a aplicação inteira
(8 erros) por causa do bug real #5 — `ObjectMapper` do Jackson 2 nunca
foi registrado como bean pelo Spring Boot 4 (que usa Jackson 3 por
padrão).
3ª tentativa (21/09/2026, 23:37): **`BUILD SUCCESS`, 13 testes, 0 falhas,
0 erros** — os dois bugs reais estavam mesmo corrigidos. Fase 5 encerrada
sem ressalva de compilação/contexto pendente (os testes de integração do
próprio fluxo de autenticação — login, refresh, etc. — ainda não foram
escritos, ver "Próximos passos").

> **Nota sobre uma tentativa seguinte, sem Docker Desktop rodando
> (22/09/2026):** o usuário rodou `mvn clean verify` de novo depois do
> `BUILD SUCCESS` acima e viu `BUILD FAILURE` com
> `Could not find a valid Docker environment`. Isso **não é um bug real**
> — é só o Docker Desktop não estar aberto naquele momento; os três testes
> que sobem o contexto Spring completo (`ServireApiApplicationTests`,
> `FlywayMigrationIntegrationTest`, `TenantIsolationIntegrationTest`)
> dependem de um Postgres via Testcontainers, que exige o Docker rodando.
> Solução: abrir o Docker Desktop, esperar ele terminar de subir, e rodar
> `mvn clean verify` de novo. Não precisa de nenhuma mudança de código.
> **Reconfirmado em seguida** (22/09/2026, 08:14, já com o Docker Desktop
> aberto): `BUILD SUCCESS`, 13 testes, 0 falhas, 0 erros — segunda
> confirmação independente, sem qualquer mudança de código.

## ✅ Fase 6 com build real confirmado (22/09/2026, 08:36)

`mvn clean verify` do usuário depois de sincronizada: `BUILD SUCCESS`,
**13 testes, 0 falhas, 0 erros**. Isso confirma, em especial, que o
mapeamento de `funcoesHabilitadas` (array de ENUM nativo do Postgres —
ver "Risco residual" na seção da Fase 6 abaixo) subiu o contexto Spring
sem erro sob `ddl-auto: validate`, que é exatamente o tipo de
incompatibilidade que só aparece na inicialização real (mesmo padrão dos
bugs reais #1 e #5). Risco considerado resolvido.

> **Ressalva que continua valendo:** este `BUILD SUCCESS` prova que o
> código *compila* e que os 13 testes já existentes continuam passando —
> **não** que as regras de negócio novas (responsável principal único,
> substituição de responsáveis, filtros de busca) estão corretas, já que
> nenhum teste novo foi escrito para elas (decisão explícita do usuário
> de priorizar velocidade, 22/09/2026 — ver "Próximos passos").

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

**Causa raiz:** `jackson-databind` (Jackson **2**, grupo
`com.fasterxml.jackson.*`) nunca tinha sido uma dependência DIRETA do
projeto — chegava só transitivamente, via `jjwt-jackson` (biblioteca de
JWT, seção 33), que a usa como seu motor interno de
serialização/desserialização de claims. **Correção feita depois, em
21/09/2026:** a primeira versão deste texto dizia que o caminho antigo
era `spring-boot-starter-web` → `spring-boot-starter-json` — isso estava
ERRADO, e só percebi ao investigar o bug real #5 logo abaixo: a partir do
Spring Boot 4, `spring-boot-starter-web` não traz Jackson 2 nenhum, e sim
**Jackson 3** (grupo `tools.jackson.*`, pacote Java `tools.jackson.*`) —
um jar completamente diferente, sem nenhuma relação de classpath com
`com.fasterxml.jackson.databind`. O `jackson-databind` (Jackson 2) que
sumiu neste bug só existia no projeto por causa do `jjwt-jackson`, nunca
por causa do Spring Web. Ao adicionar `spring-security-test` (escopo
`test`) nesta mesma fase, o Maven passou a enxergar `jackson-databind`
por um caminho bem mais RASO da árvore de dependências (via
`spring-security-test`, que também traz Jackson 2 transitivamente) do
que o caminho antigo via `jjwt-jackson` — e a regra de mediação "nearest
definition" do Maven usa a declaração mais próxima na árvore, mesmo que
o escopo dela (`test`) seja mais restrito que o da declaração mais
distante (`compile`). Resultado: `jackson-databind` ficou disponível só
no classpath de teste, sumindo do classpath de compilação principal — um
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

**✅ Confirmado pelo `mvn clean verify` seguinte do usuário**: o erro de
compilação `package com.fasterxml.jackson.databind does not exist`
desapareceu — os 48 arquivos-fonte compilaram sem erro. Esse mesmo build
revelou o bug real #5, descrito a seguir.

### 🐛 Bug real #5 (21/09/2026): `ObjectMapper` do Jackson 2 nunca existiu como bean no Spring Boot 4 — o app usa Jackson 3

Segundo `mvn clean verify` real da Fase 5 (já com o bug #4 corrigido):
compilação passou, mas a aplicação **falhou ao subir o contexto Spring**
em todo teste que carrega o contexto inteiro (`ServireApiApplicationTests`,
`FlywayMigrationIntegrationTest`, `TenantIsolationIntegrationTest` — 8
erros no total), com:

```
Parameter 0 of constructor in br.com.servire.api.security.RestAccessDeniedHandler
required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper' that could
not be found.
```

Só os dois testes que não sobem o contexto inteiro
(`GlobalExceptionHandlerTest`, `RequestIdFilterTest`) passaram.

**Causa raiz:** `RestAuthenticationEntryPoint` e `RestAccessDeniedHandler`
(as duas classes que montam a resposta 401/403 manualmente, por rodarem
dentro da cadeia de filtros do Spring Security, fora do alcance do
`GlobalExceptionHandler`) foram escritas injetando
`com.fasterxml.jackson.databind.ObjectMapper` — a classe do **Jackson 2**
— presumindo (por hábito de projetos Spring Boot 3.x) que o Spring Boot
registraria automaticamente um bean desse tipo. Isso **deixou de ser
verdade a partir do Spring Boot 4**: o Boot 4 migrou o Jackson padrão da
aplicação para a linha **3.x** (pesquisado e confirmado em 21/09/2026 via
o blog oficial do Spring, "Introducing Jackson 3 support in Spring", e o
guia de migração oficial do Spring Boot 4.0 no GitHub wiki). No Jackson
3, os pacotes e o groupId Maven mudaram de `com.fasterxml.jackson.*` para
`tools.jackson.*` (exceto `jackson-annotations`, que continua em
`com.fasterxml.jackson.annotation`), e a auto-configuration do Spring
Boot 4 registra um bean `tools.jackson.databind.json.JsonMapper` — não
mais um `ObjectMapper` do Jackson 2. Como nenhum bean `ObjectMapper`
(Jackson 2) é registrado pelo Spring Boot 4, a injeção por construtor
falhava com `NoSuchBeanDefinitionException`.

O detalhe que tornou esse bug enganoso: a classe
`com.fasterxml.jackson.databind.ObjectMapper` REALMENTE está no
classpath do projeto (por isso a compilação passa sem erro) — só que só
por causa do `jjwt-jackson` (que ainda não suporta Jackson 3, ver
https://github.com/jwtk/jjwt/issues/1029), não por causa do Spring Web.
Ou seja: o projeto tem, de propósito, **dois Jacksons coexistindo**: o
Jackson 3 (`tools.jackson.*`), usado pelo Spring para serializar as
respostas HTTP da própria API; e o Jackson 2 (`com.fasterxml.jackson.*`),
usado só internamente pelo `jjwt-jackson` para (de)serializar claims de
JWT — os dois nunca deveriam se misturar no código da aplicação.

**Correção:** trocar a injeção, em `RestAuthenticationEntryPoint` e
`RestAccessDeniedHandler`, de `com.fasterxml.jackson.databind.ObjectMapper`
(Jackson 2) para `tools.jackson.databind.json.JsonMapper` (Jackson 3) —
o mesmo bean que o Spring Boot 4 já registra e usa internamente para
serializar todas as outras respostas da API (inclusive as do
`GlobalExceptionHandler`, que nunca precisou de import direto). Nenhuma
outra mudança de código foi necessária: `JsonMapper.writeValue(Writer,
Object)` tem a mesma assinatura de conveniência que `ObjectMapper` já
tinha no Jackson 2.

**✅ Confirmado pelo `mvn clean verify` seguinte do usuário (21/09/2026,
23:37): `BUILD SUCCESS`, 13 testes, 0 falhas, 0 erros.** Os dois bugs reais
da Fase 5 (#4 e #5) estão corrigidos e confirmados — nenhuma ressalva de
compilação/contexto pendente nesta fase.

## Fase 6 — voluntários e responsáveis (seção 37/38/39/106 do plano mestre)

Primeiro módulo de negócio migrado do Angular/Supabase para o backend
próprio (seção 83: "RPCs de negócio e regras → migrar para Java"). Baseado
no levantamento real do Angular atual (`claude/DOCUMENTACAO-COMPLETA-PARA-IA.md`,
seção 10 — `VoluntariosService`), para reproduzir o mesmo comportamento
funcional, não redesenhar do zero.

**Entidades:**
- `Voluntario` (pacote `br.com.servire.api.voluntario`) — até a Fase 5
  só mapeava 4 colunas (o mínimo para o teste de isolamento); agora mapeia
  todas as colunas de `voluntarios` (V003): tipo (`COROINHA`/`ACOLITO`/
  `AMBOS`, ENUM nativo), dados de catequese/eucaristia/crisma, endereço,
  contato, horário de estudo, observações, autorização de WhatsApp e
  `funcoesHabilitadas` (array do ENUM nativo `funcao_escala`).
- `Responsavel` (nova) — mapeia `responsaveis` (V004): parentesco, nome,
  contato, `principal`. Relação `@OneToMany` bidirecional com `Voluntario`
  (`cascade = ALL`, `orphanRemoval = true`) — ver por quê em
  "Regra de responsável principal" abaixo.

**Regra de responsável principal (seção 38):** deve existir exatamente um
responsável com `principal = true` por voluntário. O banco só impede
mais de um (índice único parcial `uq_responsavel_principal_por_voluntario`
em V004) — não impede zero. Quem garante "exatamente um" é
`VoluntarioService`, validando a lista inteira antes de persistir, dentro
da mesma transação que grava o voluntário (seção 39: voluntário +
responsáveis devem ser consistentes juntos). Criar ou atualizar um
voluntário com zero ou mais de um principal falha com `400 Bad Request`
antes de tocar no banco.

**Substituição de responsáveis:** `POST`/`PUT /voluntarios` recebem a
lista COMPLETA de responsáveis desejada — o serviço apaga os antigos e
grava os novos (`orphanRemoval = true` faz o Hibernate deletar quem sai
da coleção), reproduzindo o mesmo padrão "apaga tudo e reinsere"
(`replaceResponsaveis`) que o Angular atual já usa. Não é um PATCH
incremental.

**Endpoints (todos autenticados — não estão em `ROTAS_PUBLICAS` de
`SecurityConfig`):**

| Verbo/rota | Uso |
|---|---|
| `GET /voluntarios?ativo=&tipo=&nome=` | Lista com filtros opcionais (equivalente a `VoluntariosService.list`/`.active`) |
| `GET /voluntarios/count?ativo=` | Equivalente a `countByActive` |
| `GET /voluntarios/{id}` | Detalhe com responsáveis aninhados (equivalente a `getById` com `select('*, responsaveis(*)')`) |
| `POST /voluntarios` | Cria voluntário + responsáveis numa transação |
| `PUT /voluntarios/{id}` | Atualiza voluntário + substitui responsáveis |
| `PATCH /voluntarios/{id}/ativo?ativo=` | Equivalente a `setActive` |

**Deliberadamente fora do escopo desta primeira versão** (documentado
também no javadoc de `VoluntarioController`):
- Upload real de foto / signed URL — depende do Storage (Fase 7, seção
  107). `fotoPath` existe na entidade e na resposta, mas nenhum endpoint
  escreve nele ainda.
- Filtro de listagem por `funcoesHabilitadas` (o "picker de candidatos"
  da seção 49) — só faz sentido junto do módulo de escalas (Fase 9).
- `GET .../commitments` (histórico de compromissos, view
  `vw_voluntario_compromissos`) — depende de `escalas`/`escala_eventos`/
  `escala_vagas`, que só existem no banco, sem entidade JPA ainda (Fase 9).

### ✅ Risco residual confirmado: array de ENUM nativo do Postgres (`funcoesHabilitadas`)

Este é o mapeamento mais arriscado da fase — por isso tinha ficado de
fora da entidade `Voluntario` mínima desde a Fase 4 (a javadoc antiga já
avisava disso). Pesquisado em 21-22/09/2026: combinar
`@JdbcTypeCode(SqlTypes.ARRAY)` com `@JdbcType(PostgreSQLEnumJdbcType.class)`
tem um bug conhecido no Hibernate 7.x — `ClassCastException` em
`getJdbcLiteralFormatter`, reportado no fórum oficial do Hibernate ORM
contra a versão 7.2.1.Final ("PostgreSQL array of enums does not work
with @JdbcType(PostgreSQLEnumJdbcType.class) but works with @Enumerated +
@ColumnTransformer"). A solução confirmada nessa mesma thread —
`@JdbcTypeCode(SqlTypes.ARRAY)` + `@Enumerated(EnumType.STRING)` +
`@ColumnTransformer(write = "?::funcao_escala[]")` — é a adotada em
`Voluntario.funcoesHabilitadas`.

Não foi possível confirmar isso rodando de verdade no momento em que foi
escrito — este ambiente de pesquisa não tem acesso a um Postgres real
nem à versão exata do projeto (Hibernate ORM 7.4.5.Final) para testar.
Como o projeto usa `ddl-auto: validate` (Hibernate confere o mapeamento
contra o schema real na inicialização), qualquer incompatibilidade aqui
derrubaria o contexto Spring já na subida — exatamente como os bugs
reais #1 e #5 já mostraram acontecer com outros mapeamentos.

**✅ Confirmado pelo `mvn clean verify` do usuário (22/09/2026, 08:36):
`BUILD SUCCESS`, 13 testes, 0 falhas, 0 erros.** O contexto Spring subiu
sem erro de schema/tipo nesta coluna — o mapeamento
`@JdbcTypeCode(SqlTypes.ARRAY)` + `@Enumerated(EnumType.STRING)` +
`@ColumnTransformer` funciona de verdade contra Hibernate ORM
7.4.5.Final + Postgres real. Risco encerrado.

**Ressalva que continua valendo:** este `BUILD SUCCESS` confirma que o
código compila e sobe o contexto corretamente, mas nenhum teste novo
cobre as regras de negócio da Fase 6 (responsável principal único,
substituição de responsáveis) — decisão explícita do usuário de adiar
testes, ver "Próximos passos".

## ⏳ Fase 7 — storage de fotos (seção 107 do plano mestre)

Upload/leitura de foto de voluntário/inscrição via Supabase Storage,
chamado diretamente por `RestClient` (não pelo SDK/protocolo S3 completo —
decisão deliberada de manter a stack enxuta, seção 15).

**Entidades/classes novas** (pacote `br.com.servire.api.storage`):
`StorageProperties` (`bucket`, `base-url`, `service-role-key`,
`signed-url-ttl`, `max-file-size-bytes`, `allowed-mime-types` — valores
padrão iguais aos já confirmados em produção para `voluntarios-fotos`,
V015: privado, 5 MB, jpeg/png/webp/heic), `StorageService` (interface) +
`SupabaseStorageService` (implementação), `StorageException`.

**Endpoints novos em `VoluntarioController`:** `POST /voluntarios/{id}/foto`
(multipart) grava a foto e atualiza `foto_path`; `GET
/voluntarios/{id}/foto-url` devolve uma URL assinada temporária (o bucket é
privado). `InscricaoService` (Fase 8) reusa o mesmo `StorageService` para a
foto do formulário público.

**Configuração:** `SUPABASE_URL`/`SUPABASE_SERVICE_ROLE_KEY` sem valor
padrão em `application.yml` (mesma regra de `JWT_SECRET`/`DB_URL`, seção
90) — vazios em dev/test, obrigatórios via variável de ambiente em
produção. Upload de multipart exigiu subir
`spring.servlet.multipart.max-file-size`/`max-request-size` (padrão do
Spring Boot é 1 MB, abaixo do limite de 5 MB do bucket) para 6 MB.

### 🐛 Bug real #6 (22/09/2026): `RestClient.Builder` não é auto-configurado só pelo `spring-boot-starter-web`

Confirmado pelo primeiro `mvn clean verify` do usuário depois das Fases
7/8/9 (22/09/2026, 09:14-09:16): **`BUILD FAILURE`, 13 testes, 0 falhas,
8 erros** — todos os testes que sobem o contexto Spring inteiro falharam
com a mesma causa raiz:

```text
NoSuchBeanDefinitionException: No qualifying bean of type
'org.springframework.web.client.RestClient$Builder' available
```

Isso confirma exatamente o risco já apontado na javadoc de
`SupabaseStorageService`/`TurnstileService` ao escrevê-las: diferente do
suposto, `spring-boot-starter-web` sozinho **não** auto-configura um bean
`RestClient.Builder` neste projeto (Spring Boot 4.1.1) — mesma categoria
de surpresa já vivida com `HibernatePropertiesCustomizer`/
`spring-boot-starter-flyway` (Fase 5/6), onde uma peça de infraestrutura
que "vinha de graça" no Boot 3 precisou ser resolvida à mão no Boot 4.1.

**Correção:** nova classe `br.com.servire.api.web.RestClientConfiguration`
declarando o bean manualmente (`RestClient.builder()`), em vez de depender
de qual módulo do Boot 4 traz — ou não — essa auto-configuration.

> ✅ **Confirmado corrigido por um novo `mvn clean verify` real** (22/09/2026,
> 09:24): `BUILD SUCCESS`, 13 testes, 0 falhas, 0 erros.

### 🐛 Bug real #7 (22/09/2026): barra do caminho do arquivo virava `%2F` na URL do Supabase Storage

Encontrado escrevendo `SupabaseStorageServiceTest` (débito técnico de
testes da Fase 7, pago só agora na Fase 10) — **não** por um `mvn clean
verify` real, já que essa classe é um teste unitário puro (sem Spring
context, sem Testcontainers).

Os três métodos de `SupabaseStorageService` (`armazenar`,
`gerarUrlAssinada`, `excluir`) originalmente montavam a URI assim:

```java
.uri(properties.baseUrl() + "/storage/v1/object/{bucket}/{caminho}", properties.bucket(), caminho)
```

tratando `{caminho}` como **uma única variável de template**. O problema:
`caminho` sempre contém `/` de verdade (ex.: `voluntarioId + "/perfil-" +
timestamp + extensão`, ver `VoluntarioService#definirFoto`) — e o
`UriComponentsBuilder` por trás do `RestClient` codifica esse `/` como
`%2F` ao expandir uma única variável de template, comportamento padrão e
documentado do Spring (não um bug do Spring em si). Isso teria quebrado
**toda** chamada real ao Supabase Storage, já que a API espera os
segmentos separados por `/` de verdade na URL, não `%2F` literal.

**Correção:** embutir `bucket`/`caminho` diretamente na string da URI, sem
placeholder de template para eles:

```java
.uri(properties.baseUrl() + "/storage/v1/object/" + properties.bucket() + "/" + caminho)
```

Seguro porque os dois valores vêm de dados controlados pela própria
aplicação (nome de bucket fixo em configuração; caminho montado só com
UUID + sufixo fixo + extensão de uma lista permitida, nunca com conteúdo
arbitrário do usuário) — não há necessidade de escapar caracteres
especiais além de preservar as barras como separadoras de verdade.

> ⏳ **Ainda não confirmado por um `mvn clean verify` real** —
> `SupabaseStorageServiceTest` cobre isso com `MockRestServiceServer` (URL
> literal esperada com barras, não `%2F`), mas só um teste contra um
> projeto Supabase de verdade fecharia esse risco por completo (ver
> "Riscos residuais" abaixo, item 1, que continua de pé).

### ⚠️ Riscos residuais a verificar no próximo build real

1. **Formato exato da API REST do Supabase Storage.** `SupabaseStorageService`
   usa três endpoints (`POST /storage/v1/object/{bucket}/{caminho}` para
   upload com header `x-upsert: true`, `POST /storage/v1/object/sign/{bucket}/{caminho}`
   para gerar URL assinada, `DELETE /storage/v1/object/{bucket}/{caminho}`
   para excluir) baseados na documentação pública do Supabase Storage —
   **não foi possível confirmar contra um projeto Supabase real neste
   ambiente de pesquisa.** Se o upload/assinatura falhar com um erro de
   formato de request/response, `SupabaseStorageService` é o primeiro
   lugar a checar (tem javadoc detalhado apontando exatamente isso).
2. **Upload de foto não participa da transação SQL** — documentado
   deliberadamente assim (mesmo texto já usado na javadoc de
   `VoluntarioService`): se o commit do banco falhar depois de um upload
   bem-sucedido, fica um arquivo órfão no bucket. Aceitável, não corrigido
   nesta rodada.

> ✅ **Débito de testes desta fase pago na Fase 10** (22/09/2026):
> `SupabaseStorageServiceTest` cobre os três endpoints (upload, URL
> assinada, exclusão best-effort), as validações de arquivo
> (content-type/tamanho/vazio) e o "falha alto e cedo" de configuração
> ausente — foi justamente escrevendo esse teste que o **Bug real #7**
> acima foi encontrado. O item 1 da lista acima (formato real da API
> Supabase) continua **não verificado** — só um teste contra um projeto
> Supabase de verdade fecha esse risco.

## ⏳ Fase 8 — inscrições públicas (seção 21/44/108 do plano mestre)

Formulário público de auto-inscrição (`/public/{tenantSlug}/inscricoes`),
com fila de aprovação/rejeição interna.

**Entidades novas** (pacote `br.com.servire.api.inscricao`): `Inscricao`
(espelha quase todos os campos de `Voluntario` + campos de auditoria de
aprovação/rejeição, `StatusInscricao` ENUM nativo PENDENTE/APROVADA/
REJEITADA) e `InscricaoResponsavel` (mesmo formato de `Responsavel`, mas
para uma inscrição). Mesma regra "exatamente um responsável principal"
(seção 38) reaplicada em `InscricaoService`.

**Barreiras do endpoint público, nesta ordem** (a mais barata primeiro):
1. **Rate limit por IP** (`InscricaoRateLimiter`) — em memória, janela
   deslizante, 5 tentativas/hora por padrão
   (`servire.rate-limit.inscricao-publica`). Deliberadamente **sem**
   Bucket4j/Redis (seção 45 só lista Bucket4j como uma opção possível, e
   desaconselha Redis nesta fase) — funciona corretamente com uma única
   instância da aplicação (o cenário atual do VPS); não escala
   horizontalmente sem um repensar.
2. **Cloudflare Turnstile** (`TurnstileService`, seção 44) — **falha
   fechado de propósito**: se `servire.turnstile.secret-key` estiver vazio,
   se o token do cliente vier vazio, se a chamada ao Cloudflare falhar, ou
   se a resposta disser `success: false`, a inscrição é sempre recusada.
   Nunca existe um caminho que deixa passar sem validar.
3. Resolução do tenant pelo slug + checagem de Kill Switch (`tenant.status`
   ATIVO/TRIAL) — mesma regra da seção 28, reaplicada aqui porque o
   formulário público não passa por `JwtAuthenticationFilter`
   (`InscricaoService.criarPublica` é a única classe de negócio do projeto
   que chama `TenantContext.set`/`clear` diretamente, imitando o filtro).

**Endpoints:**

| Verbo/rota | Autenticação | Uso |
|---|---|---|
| `POST /public/{tenantSlug}/inscricoes` | Nenhuma | `multipart/form-data`: parte `dados` (JSON) + parte `foto` opcional |
| `GET /inscricoes?status=` | Autenticado | Fila de aprovação |
| `GET /inscricoes/{id}` | Autenticado | Detalhe |
| `PUT /inscricoes/{id}` | Autenticado | Edita uma inscrição ainda PENDENTE |
| `POST /inscricoes/{id}/aprovar` | Autenticado | Cria o `Voluntario` de verdade + copia responsáveis, vincula `inscricao.voluntario_id` |
| `POST /inscricoes/{id}/rejeitar` | Autenticado | Exige `motivo` (CHECK `inscricoes_rejeitada_ck`, V008) |

**Deliberadamente fora do escopo desta versão:** tabela de auditoria
dedicada para aprovação/rejeição (só um log, sem persistência própria —
pacote `auditoria/` continua um item futuro do plano mestre, seção 16).

### ⚠️ Riscos residuais a verificar no próximo build real

1. **`X-Forwarded-For` sem reverse proxy confirmado.** O rate limit lê esse
   header antes de cair para `getRemoteAddr()` — só é confiável se o
   Nginx/Caddy do VPS de produção **reescrever** (não só repassar) esse
   header; não foi possível confirmar a configuração exata do deploy
   real neste ambiente de pesquisa (checklist de deploy, seção 11/89).
2. **Formato da resposta do Cloudflare Turnstile** — `TurnstileService` lê
   a resposta como `Map<String,Object>` e olha só o campo `success`, para
   evitar depender de uma anotação de (de)serialização específica de uma
   versão do Jackson; não foi possível testar contra o Cloudflare de
   verdade neste ambiente.
3. ~~**Nenhum teste automatizado** cobre o fluxo completo de inscrição
   pública (rate limit, Turnstile, aprovação, rejeição)~~ — **pago na Fase
   10** (22/09/2026): `TurnstileServiceTest` (fail-closed em todos os
   cenários — secret ausente, token vazio, `success: false`, falha HTTP),
   `InscricaoRateLimiterTest` (janela deslizante por IP, contadores
   independentes por IP) e `InscricaoServiceIntegrationTest` (rate limit
   como primeira barreira, slug inexistente/tenant bloqueado tratados
   igual, exatamente um responsável principal, aprovar/rejeitar,
   atualização de pendente bloqueada depois de aprovada). Os itens 1 e 2
   acima (proxy real, formato real do Cloudflare) continuam **não
   verificados** — nenhum teste automatizado consegue fechar esses dois
   sem um ambiente de produção/Cloudflare de verdade.

> ℹ️ O **Bug real #6** (`RestClient.Builder` não auto-configurado, ver seção
> da Fase 7 acima) também derrubava `InscricaoService`/`TurnstileService`
> desta fase — corrigido pela mesma classe `RestClientConfiguration` e
> **confirmado** pelo `mvn clean verify` de 22/09/2026 09:24 (`BUILD
> SUCCESS`, 0 erros).

## ⏳ Fase 9 — escalas (seção 9.2/46/47/109 do plano mestre)

Escalas de serviço (semanal/mensal), com eventos (missas) e vagas
(funções a preencher por voluntário).

**Entidades novas** (pacote `br.com.servire.api.escala`): `Escala`
(`TipoEscala` ENUM nativo SEMANAL/MENSAL, `StatusEscala` ENUM nativo
RASCUNHO/FINALIZADA/CANCELADA, `@Version` para controle otimista),
`EscalaEvento` (data/horário/celebração), `EscalaVaga` (reaproveita o
mesmo `FuncaoEscala` da Fase 6, posição, voluntário opcional).

**Comportamento preservado deliberadamente:** `PUT /escalas/{id}` continua
"apaga todos os eventos/vagas e reinsere" (mesmo padrão do Angular atual,
já documentado assim desde a migration V006) — `EscalaService` implementa
isso via `clear()` + reinserção na coleção gerenciada pelo Hibernate
(`orphanRemoval = true`), nunca SQL nativo (Native Query Gate, seção 81:
qualquer `nativeQuery=true`/JDBC direto numa tabela tenant-aware bypassa o
filtro automático do `@TenantId`).

**Controle otimista (seção 47):** `Escala.version` (`@Version`, coluna
nova de V023 — não existia antes). `EscalaService.atualizar` faz uma
checagem explícita comparando a versão recebida no payload com a versão
atual no banco, devolvendo `409 CONFLICT` com a mensagem "A escala foi
alterada por outro usuário. Atualize a página." — e o próprio
`@Version` do Hibernate fica como rede de segurança contra uma corrida de
verdade entre duas requisições concorrentes (`GlobalExceptionHandler`
ganhou um handler para `ObjectOptimisticLockingFailureException` com a
mesma mensagem).

**Transições de estado:** `POST /escalas/{id}/finalizar` (só de
RASCUNHO), `POST /escalas/{id}/cancelar` (de RASCUNHO ou FINALIZADA),
`POST /escalas/{id}/reabrir` (de FINALIZADA ou CANCELADA, volta para
RASCUNHO), `DELETE /escalas/{id}` (só permitido se CANCELADA — regra
explícita do plano mestre). Edição (`PUT`) só é permitida em RASCUNHO —
uma escala FINALIZADA precisa ser reaberta antes de editar.

**Endpoints:**

| Verbo/rota | Uso |
|---|---|
| `GET /escalas?tipo=&status=&ano=&mes=` | Lista com filtros |
| `GET /escalas/{id}` | Detalhe com eventos/vagas aninhados |
| `POST /escalas` | Cria (RASCUNHO) |
| `PUT /escalas/{id}` | Substitui eventos/vagas (controle otimista) |
| `POST /escalas/{id}/finalizar` \| `/cancelar` \| `/reabrir` | Transições de estado |
| `DELETE /escalas/{id}` | Só se CANCELADA |

**Migration nova:** `V023__ajustes_fases_7_8_9.sql` — repontou as FKs
`escalas.created_by`/`inscricoes.aprovado_por`/`inscricoes.rejeitado_por`,
que ainda apontavam para `auth.users` (Supabase Auth, em remoção
progressiva, seção 32/83), para `public.usuario` (a identidade de ator
própria da aplicação desde a Fase 5); e somou `escalas.version` para o
controle otimista acima. `FlywayMigrationIntegrationTest` atualizado de 22
para 23 migrations.

**Bug real encontrado por revisão de schema (não por build), corrigido
antes de qualquer build real acusar o problema:** durante o planejamento
conjunto das Fases 7/8/9, uma releitura completa de
`V021__add_tenant_id_domain_tables.sql` revelou que a entidade
`Responsavel` (Fase 6) tinha sido escrita olhando só a migration V004
original — sem saber que V021 (Fase 3/4, anterior à própria Fase 6) já
tinha acrescentado `tenant_id NOT NULL` a `responsaveis`. A entidade,
sem mapear essa coluna, não foi pega pelo `ddl-auto: validate` do build da
Fase 6 (esse modo só falha se uma coluna MAPEADA divergir do banco — não
se a entidade ignorar uma coluna existente) — o erro só apareceria em
tempo de execução, como violação de NOT NULL, no primeiro INSERT real em
`responsaveis`. Corrigido agora, adicionando `@TenantId` a `Responsavel`
(mesmo padrão de `Voluntario`/`Inscricao`/etc.) — ver javadoc da classe
para o relato completo.

**Deliberadamente fora do escopo desta versão** (referenciado pela seção
109, itens do documento técnico): controle de faltas e disponibilidade do
voluntário — não foram desenhados a tempo nesta rodada conjunta das Fases
7/8/9; ficam para uma próxima iteração, com entidades/campos próprios
ainda a definir.

### ⚠️ Riscos residuais a verificar no próximo build real

1. ~~**Controle otimista nunca testado contra um Postgres real**~~ —
   **coberto na Fase 10** por `EscalaServiceIntegrationTest`
   (`atualizarComVersaoDivergenteLancaConflictException`, checagem
   explícita de `EscalaService`) e por
   `GlobalExceptionHandlerTest.objectOptimisticLockingFailureExceptionViraHttp409ComMensagemDeNegocio`
   (rede de segurança do `ObjectOptimisticLockingFailureException`) —
   ambos ainda **sem confirmação de build real**, só revisão manual.
2. ~~Cascata de três níveis tenant-aware~~ (`Escala` → `EscalaEvento` →
   `EscalaVaga`) — **coberta na Fase 10** por
   `TenantIsolationIntegrationTest.tenantNaoDeveEnxergarEscalaComCascataDeTresNiveisDeOutroTenant`,
   que verifica isolamento nos três níveis (o `Escala` raiz via
   `EscalaRepository`, `EscalaEvento`/`EscalaVaga` via JPQL direto, já que
   não existe repositório dedicado para os dois níveis mais profundos).
3. ~~Nenhum teste automatizado cobre o "apaga tudo e reinsere" de eventos,
   as transições de estado, nem o controle otimista~~ — **pago na Fase
   10** por `EscalaServiceIntegrationTest` (6 testes: criação com cascata,
   substituição de eventos, versão divergente, atualizar fora de
   RASCUNHO, mesmo voluntário em duas vagas do mesmo evento, e a matriz de
   transição de estado completa da seção 46/109).

## ⏳ Fase 10 — multi-tenant real (seção 110 do plano mestre)

Feita junto com o pagamento de todo o débito de testes pendente (mesma
rodada de 22/09/2026, por instrução explícita do usuário — "vamos fazer
mais a Fase 10 aí fazemos todos os testes pendentes").

**Itens da seção 110 e como cada um foi endereçado nesta rodada:**

1. **Criar segundo tenant de teste / duplicar massa mínima** — cada
   método de teste em `TenantIsolationIntegrationTest`,
   `VoluntarioServiceIntegrationTest`, `InscricaoServiceIntegrationTest`,
   `EscalaServiceIntegrationTest` e `AuthServiceIntegrationTest` cria seu
   próprio tenant descartável via `TenantRepository.saveAndFlush` — não
   existe um tenant "B" fixo e compartilhado entre testes (evita
   contaminação entre métodos, já que nenhuma dessas classes usa rollback
   automático por teste).
2. **Validar isolamento** — `TenantIsolationIntegrationTest` ganhou três
   métodos novos (ver "Riscos residuais" da Fase 9 acima e da Fase 6/8
   abaixo para o detalhe de cada um): `Responsavel` (cascata de 1 nível),
   `Escala`→`EscalaEvento`→`EscalaVaga` (cascata de 3 níveis) e
   `Inscricao`+`InscricaoResponsavel` (cascata de 1 nível) — somados ao
   teste de `Voluntario` que já existia desde a Fase 4. Todos operam no
   nível de repositório/Hibernate (não de controller), mesmo espírito do
   teste original de isolamento P0 (seção 78).
3. **Executar suíte P0** — a suíte de isolamento (`TenantIsolationIntegrationTest`)
   agora cobre as sete entidades tenant-aware que existem até a Fase 9;
   ainda pendente de rodar de fato contra o Postgres real do usuário
   (ver aviso ⏳ no topo deste README).
4. **Ajustar índices** — **nenhuma migration nova foi necessária.** A
   V021 (`add_tenant_id_domain_tables`, Fase 3/4) já cria os índices
   compostos `(tenant_id, ...)` necessários para as sete tabelas de
   domínio ao mesmo tempo em que adiciona a própria coluna `tenant_id` —
   decisão já tomada duas fases atrás, não um item deixado pendente até
   agora.
5. **Validar Storage** — `SupabaseStorageServiceTest` (débito da Fase 7,
   pago nesta rodada) cobre os três endpoints via `MockRestServiceServer`;
   foi escrevendo esse teste que o **Bug real #7** (ver seção da Fase 7)
   foi encontrado e corrigido. O formato real da API Supabase continua
   **não confirmado** contra um projeto de verdade (mesma ressalva já
   feita na Fase 7).
6. **Validar inscrição pública por slug** — coberto indiretamente pelo
   teste de isolamento de `Inscricao` (item 2 acima): dois tenants com
   slugs distintos, cada um só enxergando a própria inscrição, mais os
   testes de `InscricaoServiceIntegrationTest` que exercitam
   `criarPublica` resolvendo o tenant pelo slug (incluindo os casos de
   slug inexistente e tenant bloqueado, tratados com a mesma mensagem
   genérica por anti-enumeração).

**Débito de testes de outras fases, pago na mesma rodada** (não fazia
parte da seção 110 em si, mas foi feito junto por instrução do usuário):
`AuthServiceIntegrationTest` (Fase 5, 18 métodos — login em todos os
cenários do Kill Switch, seleção de tenant, refresh com rotação/reuso/
expiração/tenant sem vínculo, esqueci senha, redefinir senha com token
válido/já usado), `VoluntarioServiceIntegrationTest` (Fase 6, 8 métodos —
responsável principal único, "apaga tudo e reinsere", foto via
`StorageService` mockado), `EscalaServiceIntegrationTest` (Fase 9, 6
métodos, ver item 3 dos "Riscos residuais" da Fase 9 acima).

> ⏳ **Nada nesta seção deve ser tratado como "pronto"** até um
> `mvn clean verify` real confirmar que todo esse código novo sequer
> compila — `mvn` não está disponível neste ambiente de pesquisa (Maven
> Central bloqueado), então a única verificação possível aqui foi revisão
> manual linha por linha, mais um script auxiliar para checar contagens de
> argumento de construtores/records (que já pegou e ajudou a corrigir três
> bugs reais de contagem de argumento antes mesmo de chegar num build).

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

Os testes de integração (`AbstractIntegrationTest` e suas subclasses —
`TenantIsolationIntegrationTest`, `VoluntarioServiceIntegrationTest`,
`InscricaoServiceIntegrationTest`, `EscalaServiceIntegrationTest`,
`AuthServiceIntegrationTest`, `FlywayMigrationIntegrationTest`,
`ServireApiApplicationTests`) precisam de Docker disponível
(Testcontainers sobe um Postgres real). Os testes puramente unitários
(`RequestIdFilterTest`, `GlobalExceptionHandlerTest`,
`InscricaoRateLimiterTest`, `TurnstileServiceTest`,
`SupabaseStorageServiceTest` — nenhum destes três últimos toca banco nem
sobe o Spring context, usam `MockRestServiceServer`/só memória) não
precisam.

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
    Voluntario.java, VoluntarioRepository.java, VoluntarioService.java, VoluntarioController.java
    Responsavel.java, ResponsavelRepository.java
    TipoVoluntario.java, FuncaoEscala.java
    dto/   <- VoluntarioRequest, VoluntarioResponse, ResponsavelRequest, ResponsavelResponse, FotoUrlResponse
  storage/   <- Fase 7
    StorageProperties.java, StorageConfiguration.java
    StorageService.java (interface), SupabaseStorageService.java, StorageException.java
  inscricao/   <- Fase 8
    Inscricao.java, InscricaoRepository.java, InscricaoResponsavel.java, InscricaoResponsavelRepository.java
    StatusInscricao.java, InscricaoService.java
    PublicInscricaoController.java, InscricaoController.java
    TurnstileProperties.java, TurnstileService.java
    RateLimitProperties.java, InscricaoRateLimiter.java, InscricaoConfiguration.java
    dto/   <- InscricaoPublicaRequest, InscricaoAtualizarRequest, InscricaoRejeitarRequest, InscricaoResponse,
              InscricaoResponsavelRequest, InscricaoResponsavelResponse
  escala/   <- Fase 9
    Escala.java, EscalaEvento.java, EscalaVaga.java, EscalaRepository.java
    TipoEscala.java, StatusEscala.java, EscalaService.java, EscalaController.java
    dto/   <- EscalaRequest, EscalaEventoRequest, EscalaVagaRequest, EscalaResponse, EscalaEventoResponse, EscalaVagaResponse
src/main/resources/
  application.yml, application-dev.yml, application-prod.yml
  logback-spring.xml
  db/migration/
    V001-V015.sql   <- baseline da Fase 0/1 (nunca editar)
    V016-V019.sql   <- tenant, usuario, usuario_tenant, refresh_token (Fase 3)
    V020.sql        <- seed do tenant inicial (placeholder de nome)
    V021.sql        <- tenant_id + FKs compostas nas 7 tabelas de domínio (Fase 3/4)
    V022.sql        <- password_reset_token (Fase 5)
    V023.sql        <- repontar FKs auth.users -> usuario + escalas.version (Fases 7/8/9)
src/test/java/br/com/servire/api/
  AbstractIntegrationTest.java  <- Testcontainers + stub Supabase
  ServireApiApplicationTests.java
  migration/FlywayMigrationIntegrationTest.java  <- versão JUnit das checagens de servire-database/local-dev/compare-schema.sql
  web/RequestIdFilterTest.java, GlobalExceptionHandlerTest.java
  tenant/TenantIsolationIntegrationTest.java  <- teste crítico P0 de isolamento (seção 78/79/80), ampliado na Fase 10
  auth/AuthServiceIntegrationTest.java   <- Fase 10, débito de testes da Fase 5
  voluntario/VoluntarioServiceIntegrationTest.java   <- Fase 10, débito de testes da Fase 6
  storage/SupabaseStorageServiceTest.java   <- Fase 10, débito de testes da Fase 7 (achou o Bug real #7)
  inscricao/TurnstileServiceTest.java, InscricaoRateLimiterTest.java, InscricaoServiceIntegrationTest.java   <- Fase 10, débito de testes da Fase 8
  escala/EscalaServiceIntegrationTest.java   <- Fase 10, débito de testes da Fase 9
```

`DevFixedTenantFilter` (andaime temporário da Fase 4) foi removido na
Fase 5, substituído por `security/JwtAuthenticationFilter.java`. Os
pacotes `storage/`, `inscricao/` e `escala/` entraram na Fase 7/8/9. Ainda
não existem `config/`, `backoffice/`, `arquivo/`, `billing/`,
`auditoria/` da estrutura-alvo completa (seção 16 do plano mestre).

## Próximos passos

> **Atualização (22/09/2026):** a decisão anterior de adiar testes em
> favor de velocidade foi revertida por instrução explícita do usuário —
> a Fase 10 e todo o débito de testes pendente (item 2 antigo desta lista)
> foram pagos na mesma rodada (ver seção da Fase 10 acima). O item 1
> abaixo continua sendo o próximo passo mais urgente, agora para um lote
> bem maior de código novo.

1. **Confirmar a Fase 10 + todo o débito de testes pago junto com um
   `mvn clean verify` real** — é o próximo passo imediato depois desta
   rodada (ver o aviso ⏳ no topo deste README, a seção da Fase 10, e os
   "Riscos residuais" de cada fase acima). Até essa confirmação chegar,
   nada desta rodada deve ser tratado como "pronto" — nem sequer que o
   código compila, já que `mvn` não está disponível neste ambiente de
   pesquisa.
2. Roles/permissões de verdade aplicadas a endpoints de negócio (seção
   31) — hoje `authorizeHttpRequests` só distingue autenticado/não
   autenticado, sem checar a role do vínculo `usuario_tenant` (vale para
   todos os módulos, incluindo aprovar/rejeitar inscrição e escalas).
3. Decidir um provedor de e-mail real para substituir `LoggingEmailSender`.
4. Desenhar controle de faltas e disponibilidade do voluntário
   (referenciados pela seção 109, deliberadamente descopados da Fase 9
   nesta rodada — ver seção da Fase 9 acima).
5. Confirmar de verdade contra um Supabase real: o formato da API REST do
   Storage (Fase 7) e a resposta do Cloudflare Turnstile (Fase 8) — ambos
   implementados a partir de documentação pública, nunca testados contra
   o serviço real neste ambiente de pesquisa.
6. Tabela de auditoria dedicada (`auditoria/`, seção 16) para
   aprovação/rejeição de inscrição — hoje só um log, sem persistência
   própria.
7. Validar a configuração real do reverse proxy do VPS de produção
   (Nginx/Caddy, seção 11/89) quanto a `X-Forwarded-For` — o rate limit da
   Fase 8 depende de esse header vir reescrito pelo proxy, não só
   repassado do cliente.
