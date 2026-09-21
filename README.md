# Servire API — Fase 2 (fundação Spring Boot)

Este projeto é o resultado da **FASE 2** do `plano_mestre_servire_v2_mvp_baixo_custo.md`
(seção 102/125): a fundação do backend Java, antes de migrar qualquer
módulo de negócio (voluntário, escala, inscrição — isso é Fase 6 em
diante, depois do modelo SaaS lógico e do TenantContext nas Fases 3/4).

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

## ✅ Build verificado (21/09/2026): `mvn clean verify` passou, 11/11 testes

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

**Observação não bloqueante:** os logs mostraram cada classe de teste
subindo seu próprio container Postgres (portas diferentes), em vez de
compartilhar um único container "singleton" como o `AbstractIntegrationTest`
pretendia — só deixa o build ~1-2s mais lento por classe, não afeta
corretude. Investigar/ajustar numa fase futura se o número de classes de
teste crescer o suficiente pra doer.

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
    ApiException.java (+ subclasses: ResourceNotFoundException, ConflictException, BadRequestException, ForbiddenException)
src/main/resources/
  application.yml, application-dev.yml, application-prod.yml
  logback-spring.xml
  db/migration/V001-V015.sql   <- baseline da Fase 0/1
src/test/java/br/com/servire/api/
  AbstractIntegrationTest.java  <- Testcontainers + stub Supabase
  ServireApiApplicationTests.java
  migration/FlywayMigrationIntegrationTest.java  <- versão JUnit das checagens de servire-database/local-dev/compare-schema.sql
  web/RequestIdFilterTest.java, GlobalExceptionHandlerTest.java
```

Ainda não existem pacotes `config/`, `security/`, `tenant/`, `auth/`,
`voluntario/` etc. da estrutura-alvo completa (seção 16 do plano mestre) —
eles entram progressivamente a partir da Fase 3.

## Próximos passos (Fases 3-4, seções 103-104/126)

1. Modelo SaaS lógico: entidades `tenant`, `usuario`, `usuario_tenant`,
   `refresh_token` + suas migrations (`V016` em diante — nunca editar
   `V001`-`V015`, que já são baseline "aplicado").
2. `TenantContext` + `CurrentTenantIdentifierResolver` do Hibernate.
3. Adicionar `tenant_id` às 7 tabelas de domínio existentes.
4. Só depois disso migrar os módulos de negócio (voluntário, inscrição,
   escala) do Angular/Supabase para cá.
