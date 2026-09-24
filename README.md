# Servire API — Fases 2 a 11 (fundação + modelo SaaS lógico + multi-tenancy + autenticação própria + voluntários + storage + inscrições + escalas + multi-tenant real + permissões/faltas/disponibilidade/auditoria/e-mail)

Este projeto cobre a **FASE 2** (fundação Spring Boot, seção 102/125), a
**FASE 3** (modelo SaaS lógico, seção 103/126), a **FASE 4**
(`TenantContext`/`@TenantId`, seção 104/126), a **FASE 5** (autenticação
própria, seção 105/32-36), a **FASE 6** (voluntários/responsáveis, seção
37/38/106), a **FASE 7** (storage de fotos, seção 107), a **FASE 8**
(inscrições públicas, seção 44/108), a **FASE 9** (escalas, seção
46/47/109), a **FASE 10** (multi-tenant real, seção 110) e a **FASE 11**
(roles/permissões reais, controle de faltas, disponibilidade do
voluntário, tabela de auditoria e provedor de e-mail real, seção
31/59/122/131.5) do `plano_mestre_servire_v2_mvp_baixo_custo.md`.

> ⏳ **Fase 11 (22/09/2026) — AINDA NÃO CONFIRMADA por `mvn clean verify`
> real.** Cinco funcionalidades implementadas nesta rodada, por pedido
> explícito do usuário ("Pode fazer todos os itens do 1 ao 5"), sem testes
> automatizados novos ainda (decisão explícita do usuário: "vamos
> desenvolver umas 4 ou 5 coisas e aí implementamos os testes dessa novas
> funcionalidades" — o débito de testes desta fase fica para a próxima
> rodada). Ver seção própria "Fase 11" abaixo para o detalhe de cada uma
> e "Próximos passos" para o que falta confirmar.
>
> 🔴 **Primeira rodada de `mvn clean verify` real da Fase 11 (22/09/2026,
> 14:05): `BUILD FAILURE`, `Tests run: 75, Failures: 1, Errors: 0`.**
> Único problema encontrado — **Bug real #11 (teste desatualizado, não
> produção):** `FlywayMigrationIntegrationTest.deveTerAplicadoTodasAsMigrationsDoBaselineComSucesso`
> tinha o total de migrations fixado em `23`, e a Fase 11 somou três
> migrations novas (V024/V025/V026) sem atualizar esse teste — mesma
> categoria de esquecimento já registrada nas duas atualizações anteriores
> desse mesmo contador (21→22, 22→23). Corrigido: total `23` → `26`, e
> `descricoes.getLast()` de `"ajustes fases 7 8 9"` para
> `"table audit log"` (descrição que o Flyway deriva do nome do arquivo
> `V026__table_audit_log.sql`). **Nenhum código de produção foi tocado por
> essa correção.**
>
> ✅ **Segunda rodada de `mvn clean verify` real da Fase 11 (22/09/2026,
> 14:08): `BUILD SUCCESS`, `Tests run: 75, Failures: 0, Errors: 0`.**
> Confirma o fix do Bug real #11. Importante ser preciso sobre o que esse
> `BUILD SUCCESS` prova e o que **não** prova sobre as cinco
> funcionalidades da Fase 11 — nenhum teste novo foi escrito nesta rodada
> (decisão explícita do usuário), então tudo que ficou confirmado foi por
> efeito colateral dos testes que já existiam:
> - **Confirmado:** os schemas dos três novos tipos nativos do Postgres
>   (`presenca_vaga`, `periodo_dia`) e o array `text[]` de
>   `audit_log.changed_fields` batem com o mapeamento JPA — isso é
>   garantido porque `ddl-auto: validate` roda em TODA subida de contexto
>   Spring, e vários testes de integração existentes sobem o contexto.
>   Também confirmado: o contexto Spring sobe corretamente com os dois
>   beans concorrentes de `EmailSender` (`LoggingEmailSender`/
>   `ResendEmailSender`) sob `@ConditionalOnProperty` sem colidir, e com
>   `@EnableMethodSecurity` ativo. E, como `VoluntarioServiceIntegrationTest`/
>   `EscalaServiceIntegrationTest`/`InscricaoServiceIntegrationTest` chamam
>   `criar`/`atualizar`/`aprovar`/etc. — que agora chamam
>   `AuditLogService.registrar` internamente — fica confirmado que
>   `registrar()` executa sem erro de SQL (incluindo a escrita na coluna
>   array) e que os fallbacks defensivos dele (`TenantContext.get()`,
>   `SecurityContextHolder`, `MDC`, `RequestContextHolder.getRequestAttributes()`
>   retornando `null` fora de uma requisição HTTP real) não lançam exceção
>   quando chamados a partir de um teste de serviço puro.
> - **Ainda NÃO confirmado nesta rodada de build:** a aplicação real do
>   `@PreAuthorize` em nível HTTP (nenhum teste `MockMvc`/`TestRestTemplate`
>   chama os controllers com roles diferentes para checar 200 vs 403);
>   `EscalaService.registrarPresenca` e todo o fluxo de
>   `DisponibilidadeVoluntarioService`/`DisponibilidadeVoluntarioController`
>   (nenhum teste existente chama nenhum dos dois); e `ResendEmailSender`
>   (o profile de teste força `servire.email.provider: log`, então o bean
>   do Resend nunca é instanciado nem exercitado).
>
> **Atualização (22/09/2026, à tarde):** as quatro lacunas acima já têm
> teste escrito (`security/MethodSecurityIntegrationTest`, os quatro
> testes novos de `registrarPresenca` em `EscalaServiceIntegrationTest`,
> `DisponibilidadeVoluntarioServiceIntegrationTest` e
> `ResendEmailSenderTest`) — ver seção "Fase 11" e "Próximos passos" para
> o detalhe de cada um. **Nenhum `mvn clean verify` real rodou ainda sobre
> esse código de teste novo** — trate como ⏳ até a próxima confirmação do
> usuário.
>
> 🔴 **Terceira rodada de `mvn clean verify` real da Fase 11 (22/09/2026,
> 14:34), sobre o código de teste novo acima: `COMPILATION ERROR`.**
> `MethodSecurityIntegrationTest` não compilava —
> `package org.springframework.boot.test.autoconfigure.web.servlet does not
> exist` e `cannot find symbol: class AutoConfigureMockMvc`. **Bug real
> #12 (build/dependência, não lógica de negócio):** a partir do Spring
> Boot 4, a autoconfiguration de TESTE também foi modularizada por
> tecnologia web — o mesmo raciocínio que já fez `spring-boot-starter-web`
> passar a depender de `spring-boot-webmvc` em vez de trazer tudo num jar
> monolítico. `@AutoConfigureMockMvc` saiu do pacote
> `org.springframework.boot.test.autoconfigure.web.servlet` (onde vivia
> até o Spring Boot 3.x) para um pacote e um MÓDULO Maven novos,
> `org.springframework.boot.webmvc.test.autoconfigure`, no artefato
> `spring-boot-webmvc-test` — que `spring-boot-starter-test` sozinho não
> traz mais transitivamente para aplicações web (confirmado contra a
> documentação oficial/javadoc do Spring Boot 4.1.1 em 22/09/2026, já que
> não há acesso de rede a repositórios Maven a partir deste ambiente de
> pesquisa para reproduzir o erro localmente). **Corrigido:** somada a
> dependência `org.springframework.boot:spring-boot-webmvc-test` (escopo
> `test`, sem versão própria — gerenciada pelo BOM do
> `spring-boot-starter-parent:4.1.1`) ao `pom.xml`, e atualizado o import
> de `MethodSecurityIntegrationTest` para o pacote novo. Nenhuma outra
> classe deste projeto usava `MockMvc`/`@AutoConfigureMockMvc` antes desta
> rodada, por isso esse problema nunca tinha aparecido em nenhum build
> anterior.
>
> 🔴 **Quarta rodada de `mvn clean verify` real da Fase 11 (22/09/2026,
> 14:45), depois do fix de compilação: `BUILD FAILURE`, `Tests run: 104,
> Failures: 6, Errors: 0`.** Dois problemas novos, os dois de PRODUÇÃO
> (não de teste) — e os dois só existem porque, pela primeira vez, um
> teste automatizado realmente exercitou o caminho onde eles viviam:
>
> **Bug real #13 (`GlobalExceptionHandler`, segurança/UX):**
> `@PreAuthorize` negado sempre devolvia **500** ("Ocorreu um erro
> inesperado...") em vez do **403** documentado — em produção, desde que
> a Fase 11 introduziu `@PreAuthorize`. Causa raiz: `AccessDeniedException`
> é lançada de DENTRO da invocação do método do controller (pelo proxy AOP
> de `@EnableMethodSecurity`), que roda DENTRO de
> `DispatcherServlet.doDispatch()` — o `@ExceptionHandler(Exception.class)`
> genérico de `GlobalExceptionHandler` capturava essa exceção ANTES dela
> conseguir escapar pela cadeia de filtros até `ExceptionTranslationFilter`/
> `RestAccessDeniedHandler` (a suposição documentada em `SecurityConfig` —
> de que esse handler capturaria a exceção "não importa de onde ela
> vier" — estava incompleta). Corrigido com dois `@ExceptionHandler`
> explícitos e mais específicos (`AccessDeniedException`/
> `AuthenticationException`) que apenas relançam a exceção — isso faz o
> `ExceptionHandlerExceptionResolver` do Spring MVC tratar como "não
> resolvido" e deixar a exceção original propagar de volta à cadeia de
> filtros, onde `RestAccessDeniedHandler` finalmente a intercepta de
> verdade. Detalhe completo no javadoc de
> `GlobalExceptionHandler#handleAccessDenied`.
>
> **Bug real #14 (`DisponibilidadeVoluntarioService.criar`, produção):**
> a duplicata de disponibilidade (mesmo voluntário/dia/período) devolvia
> um 500 cru (`DataIntegrityViolationException`) em vez do 409
> `ConflictException` documentado. Causa raiz: `save()` de uma entidade
> nova só agenda o `INSERT` via `entityManager.persist(...)` — com
> `FlushMode.AUTO`, o Hibernate pode adiar o flush físico até o COMMIT da
> transação, que só acontece DEPOIS do método (e do seu `try/catch`) já
> ter retornado, então o `catch (DataIntegrityViolationException)` nunca
> disparava. Mesma categoria de bug já documentada no Bug real #10
> (`VoluntarioService.substituirResponsaveis`, um `DELETE` atrasado; aqui é
> um `INSERT`). Corrigido trocando `save(...)` por `saveAndFlush(...)`.
>
> Ambos só foram encontrados porque os testes escritos nesta rodada
> (`MethodSecurityIntegrationTest` e
> `DisponibilidadeVoluntarioServiceIntegrationTest`) foram os PRIMEIROS a
> exercitar, respectivamente, uma negação real de `@PreAuthorize` e uma
> segunda chamada duplicada a `criar`.
>
> ✅ **Quinta rodada de `mvn clean verify` real da Fase 11 (22/09/2026),
> depois dos fixes dos Bugs reais #13 e #14: `BUILD SUCCESS`, `Tests run:
> 104, Failures: 0, Errors: 0`.** Confirma de verdade os dois fixes: em
> especial, que `@PreAuthorize` negado agora devolve o 403 correto (não
> mais 500) e que a duplicata de disponibilidade agora devolve o 409
> `ConflictException` documentado (não mais um `DataIntegrityViolationException`
> cru). Com isso, **as cinco funcionalidades da Fase 11 e as quatro
> lacunas de teste identificadas na rodada anterior estão todas
> confirmadas de verdade contra um Postgres real** — nenhuma ressalva de
> build pendente nesta fase. Ver "Próximos passos" para os itens que
> continuam fora do alcance de qualquer `mvn clean verify` agora são
> sobretudo o reverse proxy (`X-Forwarded-For`). Turnstile, Supabase
> Storage e Resend já foram confirmados ponta a ponta em 22/09/2026, e o
> envio pelo domínio próprio (`contato@servirea.com.br`) em 23/09/2026 —
> ver "Próximos passos" itens 3 e 4.

> ✅ **Fase 10 (multi-tenant real) + todo o débito de testes automatizados
> pendente (Fases 5-9), feitos juntos numa única rodada em 22/09/2026, por
> instrução explícita do usuário — CONFIRMADO com `mvn clean verify` real:
> `BUILD SUCCESS`, 75 testes, 0 falhas, 0 erros — reconfirmado numa quarta
> rodada independente.** Foram necessárias quatro rodadas reais de build
> até chegar aqui — ver o histórico completo logo abaixo — encontrando e
> corrigindo **três bugs reais de produção (#8, #9 e #10)** e três
> correções de teste ao longo do caminho. Nenhum `mvn`
> local está disponível neste ambiente de pesquisa (Maven Central
> bloqueado); toda correção foi feita por revisão manual a partir dos
> logs completos que o usuário enviou depois de cada rodada.
>
> **Histórico das três rodadas reais desta batelada (22/09/2026):**
> 1. 🔴 **Primeira rodada: `BUILD FAILURE`** — `Tests run: 75, Failures: 2,
>    Errors: 14`. Corrigidos quatro problemas: **Bug real #8** (produção,
>    `InscricaoService.criarPublica`, 2 erros — seção da Fase 8 abaixo),
>    **Bug real #9** (produção/segurança, reuso de refresh token não
>    revogava sessões de verdade, 1 falha — seção da Fase 5 abaixo), e
>    duas correções de teste: `codigo` de tenant fixo (não randomizado
>    como o `slug`) em `VoluntarioServiceIntegrationTest`/
>    `EscalaServiceIntegrationTest` (12 erros, `UNIQUE constraint`), e
>    `InscricaoRateLimiterTest` usando `Duration.ofNanos(1)` para simular
>    janela expirada — suposição de resolução de relógio que não se
>    confirmou no Windows do usuário (1 falha), trocado por uma janela
>    real de 50ms + `Thread.sleep(60)`.
> 2. 🔴 **Segunda rodada, depois das correções acima: `BUILD FAILURE`** —
>    `Tests run: 75, Failures: 0, Errors: 1`. Só sobrou **um** erro — a
>    correção do `codigo` duplicado parou de mascarar os outros 7 métodos
>    de `VoluntarioServiceIntegrationTest`, revelando um bug real novo:
>    **Bug real #10** (`VoluntarioService.substituirResponsaveis`, seção
>    106 abaixo) — `atualizarSubstituiTodosOsResponsaveisAntigosPelosNovos`
>    falhava com `duplicate key value violates unique constraint
>    "uq_responsavel_principal_por_voluntario"`.
> 3. ✅ **Terceira rodada, depois da correção do Bug real #10:
>    `BUILD SUCCESS`, `Tests run: 75, Failures: 0, Errors: 0`.** Todas as
>    75 classes/métodos de teste da Fase 10 + débito de testes das Fases
>    5-9 confirmados de verdade contra um Postgres real — ver seção da
>    Fase 10 abaixo para o detalhamento completo.
> 4. ✅ **Quarta rodada (22/09/2026, 13:15) — reconfirmação independente,
>    direto do terminal do usuário: `BUILD SUCCESS`, `Tests run: 75,
>    Failures: 0, Errors: 0`.** A terceira rodada acima usou a correção do
>    Bug real #10 tal como aplicada por outra IA (ver nota de autoria
>    abaixo); esta quarta rodada já roda com essa correção reformatada no
>    padrão do projeto e com o fix proativo em
>    `InscricaoService.substituirResponsaveis` — confirmando que nenhuma
>    das duas mudanças quebrou nada.
>
> **Nota sobre a autoria da correção do Bug real #10:** o usuário aplicou
> essa correção com ajuda de outra IA (por ter esgotado o limite de uso
> desta sessão no momento), e confirmou o `BUILD SUCCESS` acima. A
> correção foi reaplicada aqui com a formatação/ordem de imports do resto
> do projeto, e o mesmo bug foi corrigido proativamente por analogia em
> `InscricaoService.substituirResponsaveis` (usado por
> `atualizarPendente`) — esse segundo ponto **ainda não tem confirmação
> de build real**, já que nenhum teste automatizado exercita essa troca
> de responsável principal numa inscrição existente (ver seção da Fase 8
> abaixo).
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
>   Storage. Corrigido; **✅ confirmado pelo `BUILD SUCCESS` de 75 testes
>   descrito acima** (`SupabaseStorageServiceTest` passa 8/8).
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
> ⚠️ **Na época (09:24), isso só confirmava que o contexto Spring sobe e
> os 13 testes existentes continuavam passando — não o comportamento
> funcional das Fases 7/8/9 em si**, já que nenhum teste automatizado
> novo cobria o storage, as inscrições públicas ou as escalas. **Essa
> lacuna foi fechada pelo débito de testes pago na Fase 10, confirmado
> pelo `BUILD SUCCESS` de 75 testes descrito no topo desta seção.** Os
> únicos riscos residuais que continuam **não verificados** são os que
> nenhum teste automatizado consegue fechar sem um ambiente de
> produção/serviço externo de verdade: formato real da API do Supabase
> Storage, resposta do Cloudflare Turnstile, e confiabilidade de
> `X-Forwarded-For` em produção — ver as seções de cada fase abaixo.

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

### 🐛 Bug real #9 (22/09/2026, segurança): reuso de refresh token detectado não revogava as sessões de verdade

Encontrado pelo `mvn clean verify` real da Fase 10 (débito de testes da
Fase 5, pago só agora): `AuthServiceIntegrationTest.reusoDeRefreshTokenJaRevogadoRevogaTodasAsSessoesDoUsuario`
falhou — depois de simular o reuso de um refresh token já revogado (o
cenário de possível roubo de token, seção 36), a sessão de um SEGUNDO
dispositivo do mesmo usuário continuava válida, quando deveria ter sido
revogada junto.

**Causa raiz:** `RefreshTokenService.rotacionar` chama
`RefreshTokenRepository.revogarTodosAtivosDoUsuario` (um `UPDATE` em
massa) e, na sequência, lança `UnauthorizedException` — uma
`RuntimeException` (não verificada). Pela regra padrão do Spring, uma
`RuntimeException` que escapa de um método `@Transactional` marca aquela
transação como rollback-only; como `rotacionar` e o método que o chama
participam da MESMA transação física (propagação padrão `REQUIRED`, o
chamado entra na transação já aberta pelo chamador), o `UPDATE` de
revogação — apesar de já ter sido enviado ao banco — era desfeito pelo
rollback disparado pela exceção lançada logo depois, na mesma transação.
Ou seja: a funcionalidade de segurança "reuso de token revoga todas as
sessões" nunca funcionou de fato contra um banco real, só parecia
funcionar em revisão de código porque o `UPDATE` roda antes do `throw` —
o efeito só se perde no commit (ou na ausência dele).

**Correção:** anotar `RefreshTokenRepository.revogarTodosAtivosDoUsuario`
com `@Transactional(propagation = Propagation.REQUIRES_NEW)`, forçando
esse `UPDATE` a rodar e COMMITAR numa transação própria e independente,
imune ao rollback que a `UnauthorizedException` provoca depois na
transação do chamador. Funciona porque a chamada passa pelo proxy AOP do
próprio repositório (um bean diferente de `RefreshTokenService`),
contornando a limitação de "self-invocation ignora o proxy" que
inviabilizaria a mesma correção se tentada como um método privado dentro
de `RefreshTokenService`.

> ✅ **Confirmado pelo `mvn clean verify` real de 22/09/2026 (terceira
> rodada da batelada da Fase 10): `BUILD SUCCESS`, 75 testes, 0 falhas, 0
> erros** — `AuthServiceIntegrationTest.reusoDeRefreshTokenJaRevogadoRevogaTodasAsSessoesDoUsuario`
> passa, confirmando que a segunda sessão é revogada de verdade. Ver o
> aviso no topo deste README para o histórico completo das três rodadas.

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
| `GET /voluntarios/{id}/commitments` | Compromissos (escalas/eventos/vagas) |

**Deliberadamente fora do escopo desta primeira versão** (documentado
também no javadoc de `VoluntarioController`):
- Filtro de listagem por `funcoesHabilitadas` na lista geral de
  voluntários — o picker (seção 49) já filtra por função no evento.

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

**✅ Débito de testes desta fase pago na Fase 10 (22/09/2026), e agora com
`BUILD SUCCESS` confirmado de verdade (75 testes, 0 falhas, 0 erros —
terceira rodada da batelada, ver aviso no topo do README):**
`VoluntarioServiceIntegrationTest` (8 métodos — responsável principal
único/não-duplo, "apaga tudo e reinsere" de responsáveis, foto via
`StorageService` mockado, `setAtivo`).

### 🐛 Bug real #10 (22/09/2026): `INSERT` do novo responsável principal podia chegar ao banco antes do `DELETE` do antigo

Encontrado na **segunda rodada** de `mvn clean verify` real da batelada da
Fase 10 (depois de corrigidos os Bugs reais #8/#9 e as duas correções de
teste da primeira rodada, que até então mascaravam este problema — só o
primeiro dos 8 métodos de `VoluntarioServiceIntegrationTest` rodava por
causa do bug do `codigo` duplicado, seção acima):
`atualizarSubstituiTodosOsResponsaveisAntigosPelosNovos` falhava com

```text
ERROR: duplicate key value violates unique constraint "uq_responsavel_principal_por_voluntario"
Detalhe: Key (voluntario_id)=(...) already exists.
```

**Causa raiz:** `substituirResponsaveis` chama
`voluntario.getResponsaveis().clear()`, e o `orphanRemoval = true`
agenda os `DELETE`s dos responsáveis antigos — mas isso não obriga o
Hibernate a enviá-los ao banco imediatamente. Dentro do mesmo contexto de
persistência, o Hibernate podia enviar o `INSERT` do novo responsável
`principal = true` ANTES do `DELETE` do antigo (a ordem de flush do
Hibernate agrupa as operações por tipo, não segue a ordem cronológica em
que a coleção Java foi alterada). Por um instante, dentro da mesma
transação, existiam DOIS responsáveis `principal = true` para o mesmo
`voluntario_id` — e o índice único parcial `uq_responsavel_principal_por_voluntario`
(V004) recusava corretamente o `INSERT`. A coleção em memória já estava
certa; o estado físico do banco é que não tinha sido sincronizado na
ordem necessária.

**Correção:** um `entityManager.flush()` explícito logo após o
`clear()`, forçando o Hibernate a sincronizar os `DELETE`s pendentes com
o PostgreSQL naquele ponto — antes de qualquer novo `Responsavel` ser
adicionado à coleção. O `flush()` não faz commit (continua dentro do
mesmo `@Transactional` de `criar`/`atualizar`; um erro depois ainda
desfaz tudo), só antecipa a sincronização. Exige injetar
`jakarta.persistence.EntityManager` via `@PersistenceContext` em
`VoluntarioService`.

> ✅ **Confirmado pelo `mvn clean verify` real seguinte do usuário
> (22/09/2026): `BUILD SUCCESS`, 75 testes, 0 falhas, 0 erros** — a
> terceira e última rodada da batelada da Fase 10 (ver aviso no topo
> deste README). O usuário aplicou esta correção com ajuda de outra IA
> (sessão anterior já no limite de uso) — reaplicada aqui com a
> formatação padrão do projeto, e corrigido proativamente o mesmo bug
> (mesmo padrão "apaga tudo e reinsere") em
> `InscricaoService.substituirResponsaveis` (Fase 8), que só é exercitado
> contra uma inscrição já existente via `atualizarPendente` — ✅
> confirmado em 22/09/2026 18:50 (`BUILD SUCCESS`, 109 testes; ver
> "Próximos passos" item 2).

### 🐛 Bugs reais #15 (listagem) e filtro JWT, encontrados na validação do Storage (22/09/2026)

O roteiro manual do Storage passou por login → CSRF → `POST /voluntarios`
→ `GET /voluntarios` → `POST .../foto`. Três bloqueios **antes** do
Supabase, todos de produção:

1. **CSRF** — `POST /voluntarios` sem cookie `XSRF-TOKEN` + header
   `X-XSRF-TOKEN` devolve 403 `"Acesso negado."` (parece role/tenant;
   não é). `csrf.spa()` isenta só login/select-tenant/forgot/reset e
   `/public/**`. Tenant vai no JWT, não em `X-Tenant-ID`.
2. **Bug real #15 (`JwtAuthenticationFilter`):**
   `vinculo.getTenant().getStatus()` fora de transação, com
   `open-in-view: false` → `LazyInitializationException` → o Boot
   despacha `/error` e o cliente via 401 (`path=/error`,
   `requestId=null`). Corrigido com `@EntityGraph(attributePaths =
   "tenant")` em `UsuarioTenantRepository.findByUsuario_IdAndTenant_Id`
   e `/error` em `permitAll`.
3. **`GET /voluntarios` 500** — JPQL com `(:param IS NULL OR ...)` +
   `CONCAT`/`lower` no Hibernate 7 + Postgres: primeiro
   `function lower(bytea) does not exist`, depois
   `could not determine data type of parameter`. Corrigido: listagem
   via `JpaSpecificationExecutor` / Criteria, predicado só quando o
   filtro veio preenchido. `VoluntarioService.buscarSemFiltros…`
   cobre o caso sem query string.

## ✅ Fase 7 — storage de fotos (seção 107 do plano mestre)

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

**Configuração:** em produção, `SUPABASE_URL`/`SUPABASE_SERVICE_ROLE_KEY`
via variável de ambiente (`application-prod.yml`), sem valor padrão
(mesma regra de `JWT_SECRET`/`DB_URL`, seção 90). Em dev, **não** use
`${SUPABASE_URL:}` / `${SUPABASE_SERVICE_ROLE_KEY:}` vazios em
`application-dev.yml` — um placeholder vazio **sobrescreve** o arquivo
importado e o Supabase responde `Invalid Compact JWS`. Segredos locais
ficam em `application-dev-local.yml` na raiz do repo (gitignorado,
importado por `spring.config.import`; o example no repo só tem
placeholder). Alternativa: `SERVIRE_STORAGE_BASE_URL` /
`SERVIRE_STORAGE_SERVICE_ROLE_KEY` (binding padrão do Spring). Upload de
multipart exigiu subir `spring.servlet.multipart.max-file-size` /
`max-request-size` (padrão do Spring Boot é 1 MB, abaixo do limite de
5 MB do bucket) para 6 MB.

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

> ✅ **Confirmado pelo `BUILD SUCCESS` de 75 testes descrito no topo deste
> README** (`SupabaseStorageServiceTest` com `MockRestServiceServer`,
> URL literal esperada com barras, não `%2F`). O risco 1 da lista abaixo
> (formato real da API) foi fechado depois, contra o projeto Supabase
> de verdade — ver a confirmação de 22/09/2026 no fim desta seção.

### 🐛 Bug real #16 (22/09/2026): Kong do Supabase recusa JWT sem header `apikey` (`Invalid Compact JWS`)

Validação manual contra o projeto real `qcybebkhwhrudbwoweip`. Com
`base-url`/`service-role-key` já chegando no processo, o upload ia até
o Storage e voltava HTTP 400 com corpo
`{"statusCode":"403","error":"Unauthorized","message":"Invalid Compact JWS","code":"AccessDenied"}`
— a API Servire mapeava isso para 503 ("Não foi possível enviar o
arquivo…"). A `service_role` JWT estava bem formada (`keyLen=219`,
dois pontos). O gateway (Kong) exige **os dois** headers:
`Authorization: Bearer <key>` **e** `apikey: <key>`. Só o Bearer não
basta. Corrigido em `SupabaseStorageService.aplicarAuthSupabase` nas
três operações. A key é lida com `trim()` em `StorageProperties`.

Dois bloqueios de configuração vieram **antes** desse 400, e não são
bug do Supabase:

1. `$env:SUPABASE_*` setado noutro terminal não entra no
   `mvn spring-boot:run` — a API subia com storage vazio. Por isso
   `application-dev.yml` importa `optional:file:./application-dev-local.yml`
   (gitignorado).
2. `service-role-key: ${SUPABASE_SERVICE_ROLE_KEY:}` vazio em
   `application-dev.yml` **sobrescreve** o arquivo local (documento
   que importa ganha do importado). Removido o placeholder; a key
   fica só no arquivo local ou em `SERVIRE_STORAGE_*`.

`StorageException` agora estende `ApiException` → HTTP 503 com mensagem
clara, em vez de 500 genérico. O corpo HTTP do Supabase vai para
`target/servire-dev.log` (appender FILE do profile `dev`).

### 🐛 Bug real #17 (22/09/2026): `POST /voluntarios/{id}/foto` 500 *depois* do upload já ter funcionado

Encontrado na mesma validação. O arquivo chegou ao bucket
`voluntarios-fotos` e `GET /foto-url` já devolvia URL assinada real;
o POST ainda respondia 500 (`requestId` `b8b90269-…`). Causa:
`VoluntarioResponse.de` lê `responsaveis` no controller, já com a
transação do service fechada (`open-in-view: false`) →
`LazyInitializationException`. O mesmo vale para `GET /voluntarios`
e `GET /{id}` / `PATCH /ativo` quando a coleção não foi tocada na
transação. Corrigido com `@EntityGraph(attributePaths = "responsaveis")`
em `VoluntarioRepository.findById` e `JOIN FETCH` na Specification da
listagem. Testes em `VoluntarioServiceIntegrationTest` agora chamam
`VoluntarioResponse.de` fora da transação do service.

> ⏳ O POST /foto com 200 (JSON do voluntário + `fotoPath`) **não foi
> reconfirmado** depois desse fix — o Maven precisa ter subido com o
> bytecode novo. O upload em si e a URL assinada **já estavam ok**
> antes da correção.

### ⚠️ Riscos residuais

1. ~~**Formato exato da API REST do Supabase Storage.**~~ **Fechado em
   22/09/2026** — ver confirmação abaixo. `DELETE` de arquivo órfão
   continua só coberto por mock (`exclusaoFalhaSilenciosamente…`); não
   foi exercitado contra o projeto real.
2. **Upload de foto não participa da transação SQL** — documentado
   deliberadamente assim (mesmo texto já usado na javadoc de
   `VoluntarioService`): se o commit do banco falhar depois de um upload
   bem-sucedido, fica um arquivo órfão no bucket. Aceitável, não
   corrigido nesta rodada. A validação real mostrou o inverso também:
   upload ok + falha ao montar o JSON (Bug real #17) deixa o arquivo
   no bucket e `foto_path` gravado.

> ✅ **Débito de testes desta fase pago na Fase 10, com `BUILD SUCCESS`
> confirmado (22/09/2026):** `SupabaseStorageServiceTest` cobre os três
> endpoints via mock, as validações de arquivo e o "falha alto e cedo"
> de configuração ausente — foi escrevendo esse teste que o
> **Bug real #7** foi encontrado.

> ✅ **Confirmação real contra o projeto Supabase `qcybebkhwhrudbwoweip`
> (22/09/2026, à noite), segunda integração externa ponta a ponta
> (Turnstile foi a primeira).** Bucket privado `voluntarios-fotos`,
> profile `dev`, `application-dev-local.yml`, usuário
> `teste@teste.com` / tenant `teste-turnstile`, voluntário
> `40141eda-7449-4eca-9567-ade9d393e98c`. Resultado:
>
> - `POST /storage/v1/object/voluntarios-fotos/{uuid}/perfil-*.jpg` com
>   `x-upsert: true`, `Authorization` + `apikey` — arquivo persistido.
> - `POST /storage/v1/object/sign/...` + `{"expiresIn": 3600}` — resposta
>   `signedURL` no formato `/object/sign/{bucket}/{caminho}?token=...`
>   (JWT HS512). A URL final
>   `baseUrl + "/storage/v1" + signedURL` abre no navegador.
> - Abrir o caminho **sem** `?token=` (o terminal do Windows/VS Code
>   corta o link em `?`) devolve
>   `querystring must have required property 'token'` — não é falha da
>   API; `Start-Process` com a URL completa resolve.
> - A `foto-teste.jpg` usada no roteiro é um JPEG 1×1: o browser mostra
>   tela preta, não um erro JSON.
>
> **Não exercitado no projeto real:** `DELETE` do objeto. **Nota de
> segurança:** a `service_role` desse projeto apareceu no chat e num
> `application-dev-local.yml.example` (já sanitizado para placeholder)
> — rotacionar no painel do Supabase antes de usar em produção.

## ✅ Fase 8 — inscrições públicas (seção 21/44/108 do plano mestre)

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

### 🐛 Bug real #8 (22/09/2026): `InscricaoService.criarPublica` gravava tudo com o tenant sentinela `SEM_TENANT`

Encontrado pelo `mvn clean verify` real da Fase 10: os testes de
`InscricaoServiceIntegrationTest` que exercitam `criarPublica` falhavam
com violação da foreign key `inscricoes_tenant_id_fkey` — a inscrição
pública nunca conseguia ser gravada de fato num banco real, apesar de
todo o código parecer correto em revisão manual.

**Causa raiz:** o Hibernate resolve e FIXA o identificador de tenant de
uma sessão no momento em que a sessão é aberta — e sob `@Transactional`
declarativo do Spring, a sessão é aberta na ENTRADA do método (pelo proxy
AOP), antes do corpo do método rodar. `criarPublica` tinha
`@Transactional` no método inteiro, mas só chamava
`TenantContext.set(tenant.getId())` DENTRO do corpo, depois de resolver o
tenant pelo slug — ou seja, tarde demais: a sessão já tinha nascido presa
ao tenant sentinela `SEM_TENANT` (`00000000-0000-0000-0000-000000000000`,
usado pelo `ServireCurrentTenantIdentifierResolver` quando não há
`TenantContext` definido). O `INSERT` em `inscricoes`, adiado até o
commit da transação (que só acontece depois que o corpo do método —
incluindo o `finally` que limpa o `TenantContext` — já terminou), sempre
tentava gravar `tenant_id = SEM_TENANT`, violando a FK. **Isso significa
que a funcionalidade carro-chefe da Fase 8 nunca funcionou de fato contra
um banco real.**

Esse é exatamente o mesmo mecanismo (sessão do Hibernate resolve o tenant
uma única vez, na abertura) que já tinha sido identificado e corrigido
proativamente em dois métodos de `TenantIsolationIntegrationTest` durante
a revisão manual desta mesma rodada (removendo `@Transactional` deles,
ver seção da Fase 10 abaixo) — a correção proativa nos testes já
confirmava a teoria; faltava aplicá-la também no código de produção.

**Correção:** remover `@Transactional` do método `criarPublica` em si.
Um novo `TransactionTemplate` (construído a partir de um
`PlatformTransactionManager` injetado no construtor) abre a transação
manualmente, DEPOIS de `TenantContext.set(...)` já ter rodado — a lógica
de persistência (criar a entidade, aplicar campos, salvar, subir a foto)
foi extraída para um novo método privado `gravarInscricaoPublica`,
chamado dentro de `transactionTemplate.execute(...)`. O `try/finally` que
limpa `TenantContext`/MDC continua envolvendo tudo, exatamente como
antes.

> ✅ **Confirmado pelo `mvn clean verify` real seguinte do usuário
> (22/09/2026): `BUILD SUCCESS`, 75 testes, 0 falhas, 0 erros** —
> `InscricaoServiceIntegrationTest` passa 10/10, incluindo os métodos que
> exercitam `criarPublica` de ponta a ponta. Ver o aviso no topo deste
> README para o histórico completo das três rodadas de build desta
> batelada.

### 🐛 Bug real #10 (mesmo mecanismo, seção 106) também corrigido proativamente em `InscricaoService`

O `substituirResponsaveis` de `InscricaoService` (usado por
`atualizarPendente` contra uma inscrição PENDENTE já existente) tem o
mesmo padrão "apaga tudo e reinsere" que causou o **Bug real #10** em
`VoluntarioService` (ver seção da Fase 6) — e a tabela
`inscricao_responsaveis` tem o mesmo tipo de índice único parcial
(`ux_inscricao_responsavel_principal`, V009) que causaria o mesmo
`duplicate key value violates unique constraint` ao trocar o responsável
principal de uma inscrição existente. Corrigido por analogia, com o
mesmo `entityManager.flush()` logo após o `clear()`. ✅ **Confirmado em
22/09/2026 18:50** por
`InscricaoServiceIntegrationTest.atualizarPendenteTrocandoOResponsavelPrincipalNaoLancaConflictException`
no `mvn clean verify` real (`BUILD SUCCESS`, 109 testes, 0 falhas, 0
erros).

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

## ✅ Fase 9 — escalas (seção 9.2/46/47/109 do plano mestre)

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

**Compromissos do voluntário (22/09/2026):**
`GET /voluntarios/{id}/commitments` (`PERM_VOLUNTARIO_READ`). Mesmo
conteúdo da view `vw_voluntario_compromissos` (V010: escala, status,
data, horário, celebração, função), montado via JPA em
`EscalaVaga`+`EscalaEvento`+`Escala` para não usar SQL nativo (seção 81).
Inclui RASCUNHO/CANCELADA de propósito (dívida da view, não corrigida).
Voluntário inexistente ou de outro tenant → 404. Testes em
`CompromissoIntegrationTest`. ✅ `mvn clean verify` real de 22/09/2026
19:20: `BUILD SUCCESS`, 122 testes, 0 falhas (era 117; os 5 a mais são
compromissos + o GET de autorização).

**Picker de candidatos (seção 49, 22/09/2026):**
`GET /escalas/{eventoId}/candidatos?funcao=MISSAL` (`PERM_ESCALA_READ`).
Filtra no backend: tenant (`@TenantId`), voluntário ativo, função em
`funcoes_habilitadas`, ainda não alocado no evento, disponibilidade
compatível. Sem nenhuma linha em `disponibilidade_voluntario` o
voluntário entra (opt-in — não esvaziar o picker de quem ainda não
cadastrou). Quem cadastrou precisa bater dia (recorrente `dia_semana`
ou pontual `data`) e período (`00:00–11:59` manhã, `12:00–17:59` tarde,
`18:00+` noite). Não há matriz `tipo × função` no plano; o `tipo` só
vai na resposta. Testes em `CandidatoPickerIntegrationTest`. ✅
`mvn clean verify` real de 22/09/2026 19:13: `BUILD SUCCESS`, 117
testes, 0 falhas (era 109; os 8 a mais são o picker + o GET de
autorização).

**Alocação pontual da vaga (22/09/2026):**
`PATCH /escalas/vagas/{vagaId}` (`PERM_ESCALA_WRITE`), corpo
`{ "voluntarioId": "<uuid>" }` ou `null` para esvaziar. Complemento do
picker: grava só aquela vaga, sem o `PUT` da escala inteira. Só em
RASCUNHO (mesma regra do `PUT`; CANCELADA/FINALIZADA → 409, reabrir
antes). Recusa voluntário inativo ou já alocado em outra vaga do mesmo
evento. Trocar ou esvaziar zera `presenca` para PENDENTE. Testes em
`AlocacaoVagaIntegrationTest`. ✅ `mvn clean verify` real de 22/09/2026
19:25: `BUILD SUCCESS`, 133 testes, 0 falhas (era 122; os 11 a mais são
a alocação + os 2 de autorização).

**Endpoints:**

| Verbo/rota | Uso |
|---|---|
| `GET /escalas?tipo=&status=&ano=&mes=` | Lista com filtros |
| `GET /escalas/{id}` | Detalhe com eventos/vagas aninhados |
| `GET /escalas/{eventoId}/candidatos?funcao=` | Picker de candidatos (seção 49) |
| `PATCH /escalas/vagas/{vagaId}` | Aloca/desaloca voluntário na vaga |
| `PATCH /escalas/vagas/{vagaId}/presenca` | Marca presença/falta |
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
   (rede de segurança do `ObjectOptimisticLockingFailureException`) — ✅
   **confirmado pelo `BUILD SUCCESS` de 75 testes** (ver aviso no topo do
   README).
2. ~~Cascata de três níveis tenant-aware~~ (`Escala` → `EscalaEvento` →
   `EscalaVaga`) — **coberta na Fase 10** por
   `TenantIsolationIntegrationTest.tenantNaoDeveEnxergarEscalaComCascataDeTresNiveisDeOutroTenant`,
   que verifica isolamento nos três níveis (o `Escala` raiz via
   `EscalaRepository`, `EscalaEvento`/`EscalaVaga` via JPQL direto, já que
   não existe repositório dedicado para os dois níveis mais profundos) —
   ✅ **confirmado**, `TenantIsolationIntegrationTest` passa 5/5.
3. ~~Nenhum teste automatizado cobre o "apaga tudo e reinsere" de eventos,
   as transições de estado, nem o controle otimista~~ — **pago na Fase
   10** por `EscalaServiceIntegrationTest` (6 testes: criação com cascata,
   substituição de eventos, versão divergente, atualizar fora de
   RASCUNHO, mesmo voluntário em duas vagas do mesmo evento, e a matriz de
   transição de estado completa da seção 46/109) — ✅ **confirmado**,
   `EscalaServiceIntegrationTest` passa 6/6.

## ✅ Fase 10 — multi-tenant real (seção 110 do plano mestre)

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
   agora cobre as sete entidades tenant-aware que existem até a Fase 9; ✅
   **já rodou de fato contra o Postgres real do usuário e passou 5/5**
   (parte do `BUILD SUCCESS` de 75 testes — ver aviso no topo deste
   README).
4. **Ajustar índices** — **nenhuma migration nova foi necessária.** A
   V021 (`add_tenant_id_domain_tables`, Fase 3/4) já cria os índices
   compostos `(tenant_id, ...)` necessários para as sete tabelas de
   domínio ao mesmo tempo em que adiciona a própria coluna `tenant_id` —
   decisão já tomada duas fases atrás, não um item deixado pendente até
   agora.
5. **Validar Storage** — `SupabaseStorageServiceTest` (débito da Fase 7,
   pago nesta rodada) cobre os três endpoints via `MockRestServiceServer`;
   foi escrevendo esse teste que o **Bug real #7** (ver seção da Fase 7)
   foi encontrado e corrigido. ✅ Confirmado pelo `BUILD SUCCESS` de 75
   testes. ✅ **Formato real da API confirmado em 22/09/2026** contra o
   projeto `qcybebkhwhrudbwoweip` (upload + URL assinada + download no
   navegador) — ver seção da Fase 7. `DELETE` real e o POST /foto
   devolvendo 200 depois do Bug real #17 ainda não foram refeitos.
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

> ✅ **Confirmado com `mvn clean verify` real: `BUILD SUCCESS`, 75 testes,
> 0 falhas, 0 erros (22/09/2026).** Foram necessárias três rodadas reais
> de build — o histórico completo, com os três bugs reais de produção
> (#8, #9, #10) e as três correções de teste encontrados ao longo do
> caminho, está no aviso no topo deste README e nas seções de cada fase.
> Toda a Fase 10 + o débito de testes automatizados das Fases 5-9 estão
> agora confirmados de verdade contra um Postgres real — não só que o
> código compila, mas que as regras de negócio de cada fase funcionam
> como esperado. O fix proativo em
> `InscricaoService.substituirResponsaveis` fechou no `verify` de
> 22/09/2026 18:50 (109 testes). Os riscos que nenhum teste
> automatizado fecha sozinho e **ainda** estão abertos: a API real do
> Resend e a configuração real do reverse proxy (`X-Forwarded-For`).
> Storage e Turnstile já foram confirmados contra os serviços reais —
> ver "Próximos passos" item 3.

## ⏳ Fase 11 — permissões, faltas, disponibilidade, auditoria e e-mail real (seção 31/59/122/131.5)

Cinco funcionalidades pedidas juntas pelo usuário em 22/09/2026 ("Pode
fazer todos os itens do 1 ao 5"), **sem testes automatizados novos por
decisão explícita do usuário** (débito de testes fica para a próxima
rodada, mesmo padrão já usado nas Fases 6-9).

> 🔴 **Primeira rodada de build real: `BUILD FAILURE`** por
> `FlywayMigrationIntegrationTest` desatualizado (**Bug real #11**, teste,
> não produção) — corrigido.
>
> ✅ **Segunda rodada de build real (22/09/2026, 14:08): `BUILD SUCCESS`,
> 75 testes, 0 falhas, 0 erros.** Confirma o fix do Bug real #11 e, por
> efeito colateral dos testes já existentes, confirma que os schemas/
> mapeamentos JPA novos batem com o banco real e que o contexto Spring
> sobe corretamente com os beans/`@EnableMethodSecurity` novos — **mas
> nenhuma linha de negócio nova desta fase foi exercitada por um teste que
> a chame diretamente** (nenhum teste `MockMvc`/HTTP para `@PreAuthorize`,
> nenhum teste para `registrarPresenca`/`DisponibilidadeVoluntario`, nenhum
> teste que ative o `ResendEmailSender`). Ver o aviso completo no topo
> deste README para o detalhe do que ficou confirmado e do que continua
> pendente.
>
> 🔴 **Terceira rodada de build real, sobre os quatro testes escritos numa
> rodada posterior (22/09/2026, à tarde): `COMPILATION ERROR`** —
> **Bug real #12**: `@AutoConfigureMockMvc` mudou de pacote/módulo Maven
> no Spring Boot 4 (modularização por tecnologia web, mesmo raciocínio do
> `spring-boot-starter-web`/`spring-boot-webmvc`); `spring-boot-starter-test`
> sozinho não traz mais essa classe. Corrigido somando
> `spring-boot-webmvc-test` (escopo `test`) ao `pom.xml` e atualizando o
> import em `MethodSecurityIntegrationTest` para o pacote novo
> (`org.springframework.boot.webmvc.test.autoconfigure`). Ver o aviso
> completo no topo deste README.
>
> 🔴 **Quarta rodada de build real, depois do fix de compilação:
> `BUILD FAILURE`, 6 testes falhando.** Dois bugs reais de PRODUÇÃO — não
> de teste — encontrados pela primeira vez porque os testes desta rodada
> foram os primeiros a exercitar esses caminhos: **Bug real #13**
> (`@PreAuthorize` negado devolvia 500 em vez de 403 — o
> `@ExceptionHandler(Exception.class)` genérico capturava
> `AccessDeniedException` antes dela chegar em
> `RestAccessDeniedHandler`) e **Bug real #14**
> (`DisponibilidadeVoluntarioService.criar` devolvia 500 cru em vez de 409
> numa duplicata — `save()` não força o `INSERT` a tempo do `try/catch`
> pegar a violação de constraint). Ver o aviso completo no topo deste
> README para o detalhe de cada um. Corrigidos; ainda não confirmados —
> aguardando o próximo `mvn clean verify` do usuário.

### 1. Roles e permissões reais (seção 31)

Até a Fase 10, `SecurityConfig` só distinguia autenticado/não autenticado
(`anyRequest().authenticated()`) — a role do vínculo `usuario_tenant`
(`ADMIN`/`COORDENADOR`/`VISUALIZADOR`) já existia e ia parar no JWT desde
a Fase 5, mas nenhum endpoint checava nada além de "tem token válido".

Implementado:
- `security/Permissao.java` — as 7 permissões conceituais da seção 31
  (`VOLUNTARIO_READ/WRITE`, `ESCALA_READ/WRITE`, `INSCRICAO_READ/APPROVE`,
  `CONFIG_WRITE`).
- `security/RolePermissoes.java` — o mapeamento `role -> permissions`
  (critério de bom senso documentado no javadoc da classe, já que a seção
  31 não detalha o mapeamento exato): `ADMIN` tem todas; `COORDENADOR` tem
  todas menos `CONFIG_WRITE`; `VISUALIZADOR` só as `*_READ`.
- `JwtAuthenticationFilter` agora concede uma `GrantedAuthority`
  `PERM_<permissão>` por permissão da role, além do `ROLE_<role>` que já
  existia.
- `SecurityConfig` ganhou `@EnableMethodSecurity` (liga `@PreAuthorize`).
- `VoluntarioController`, `EscalaController` e `InscricaoController`
  ganharam `@PreAuthorize("hasAuthority('PERM_...')")` em cada método —
  leitura exige a permissão `*_READ`, escrita exige `*_WRITE` (ou
  `INSCRICAO_APPROVE`, já que a seção 31 não define uma `INSCRICAO_WRITE`
  própria — decisão documentada no javadoc de `InscricaoController`).
- `GET`/`PUT /tenant` (`PERM_CONFIG_WRITE`) — nome, razão social e CNPJ
  da paróquia do JWT. `codigo`/`slug`/`status` só na resposta (slug
  quebra URL pública; status é Kill Switch). Tabela `tenant` é global:
  o serviço lê só o id do `TenantContext`, nunca do path. COORDENADOR
  não entra (só ADMIN tem `CONFIG_WRITE`). Testes em
  `TenantServiceIntegrationTest`. ✅ `mvn clean verify` real de
  22/09/2026 19:31: `BUILD SUCCESS`, 140 testes, 0 falhas (era 133;
  os 7 a mais são o tenant + autorização).

**Risco residual:** uma `AccessDeniedException` de `@PreAuthorize` deveria
cair no mesmo `RestAccessDeniedHandler` já usado por
`authorizeHttpRequests` (comportamento padrão documentado do
`ExceptionTranslationFilter` do Spring Security) — **não confirmado contra
uma chamada HTTP real** (nenhum teste `MockMvc`/`TestRestTemplate` exercita
os controllers desta rodada, só os serviços diretamente).

### 2. Controle de faltas (seção 122 item 11 / 131.5)

Coluna `presenca` (não uma tabela própria — decisão explícita, ver
comentário da migration V024) somada a `escala_vagas`, com um novo tipo
nativo do Postgres `presenca_vaga` (`PENDENTE`/`PRESENTE`/`FALTOU`).

- `escala/Presenca.java` — a enum.
- `EscalaVaga.getPresenca()`/`setPresenca()`.
- `EscalaVagaRepository` — novo, acesso direto a uma vaga (antes só existia
  via cascata a partir de `Escala`).
- `EscalaService.registrarPresenca(vagaId, presenca)` — recusa (400) marcar
  presença numa vaga sem voluntário alocado, e recusa (409) numa escala já
  `CANCELADA`.
- Endpoint: `PATCH /escalas/vagas/{vagaId}/presenca`, exige
  `ESCALA_WRITE`.

### 3. Disponibilidade do voluntário (seção 122 item 12 / 131.5)

Nova entidade tenant-aware `disponibilidade_voluntario` (migration V025),
sub-recurso de voluntário: `dia_semana` (recorrente, ex. "toda quarta") OU
`data` (exceção pontual) — nunca os dois, nunca nenhum (CHECK no banco +
validação em `DisponibilidadeVoluntarioService.criar`) — mais `periodo`
(`MANHA`/`TARDE`/`NOITE`, novo tipo nativo `periodo_dia`).

- `voluntario/Periodo.java`, `DisponibilidadeVoluntario.java`,
  `DisponibilidadeVoluntarioRepository.java`,
  `DisponibilidadeVoluntarioService.java`,
  `DisponibilidadeVoluntarioController.java`.
- Endpoints: `GET/POST /voluntarios/{voluntarioId}/disponibilidades`,
  `DELETE /voluntarios/{voluntarioId}/disponibilidades/{id}` — exigem
  `VOLUNTARIO_READ`/`VOLUNTARIO_WRITE` (a seção 31 não define uma
  permissão própria para este sub-recurso).

**Deliberadamente fora do escopo desta rodada:** integração com o picker
de candidatos (seção 49) — o endpoint do picker em si ainda não existe no
backend Java, então não há o que filtrar por disponibilidade ainda. Fica
para quando o picker for implementado.

### 4. Tabela de auditoria (seção 59)

Novo pacote `audit/` — `AuditLog.java`, `AuditLogRepository.java`,
`AuditLogService.java`, `AuditLogController.java` (migration V026).
`changed_fields` usa um array nativo `text[]` (não `jsonb` como o exemplo
da seção 59 sugere) — mesma informação, mapeamento JPA mais simples (ver
comentário da migration V026).

`AuditLogService.registrar(acao, entidade, entidadeId, changedFields)` é
chamado explicitamente (nunca via AOP genérico) a partir de
`VoluntarioService`, `EscalaService` e `InscricaoService`, depois de cada
mudança relevante (criação, atualização, ativação/desativação, foto,
finalizar/cancelar/reabrir/excluir escala, registrar presença, aprovar/
rejeitar inscrição). `changedFields` para os métodos de "atualização
completa" (ex. `VoluntarioService.atualizar`) é a lista FIXA de campos que
aquele tipo de requisição é capaz de alterar, não um diff de verdade
contra o estado anterior — simplificação deliberada, documentada no
javadoc de cada método, que ainda cumpre o objetivo da seção 59 de nunca
duplicar o conteúdo pessoal em si.

Tenant/usuário/requestId são lidos de `TenantContext`/
`SecurityContextHolder`/MDC (já garantidos setados por quem chama); IP é
lido via `RequestContextHolder` de dentro do próprio `AuditLogService` —
uma exceção deliberada ao padrão do resto do projeto de receber
IP/metadados explicitamente por parâmetro (ver javadoc da classe para o
porquê: retrofitar IP por parâmetro em toda a cadeia de 3 serviços seria
bem mais invasivo para um dado "nice to have" de auditoria).

Endpoint de consulta: `GET /audit-log` (filtros opcionais `entidade` +
`entidadeId`), restrito a `hasRole('ADMIN')` diretamente (não a uma
permissão conceitual — a seção 31 não define uma permissão própria de
auditoria).

**Risco residual:** nenhum teste exercita a gravação de auditoria em nenhum
cenário — todo esse fluxo (inclusive o `RequestContextHolder` dentro de
`AuditLogService`, que só funciona de verdade dentro de uma requisição
HTTP real) está **sem qualquer confirmação de build**.

### 5. Provedor de e-mail real: Cloudflare (DNS) + Resend (envio)

Decisão tomada com o usuário em 22/09/2026 (proposta do próprio usuário,
"A Combinação Ideal (Custo R$ 0,00)", validada contra a documentação
pública do Resend antes de implementar — endpoint, formato de autenticação
e limites do free tier conferem). Domínio `servirea.com.br` (seção 122
item 16) **registrado em 22/09/2026** (confirmado pelo usuário, print do
painel do registro.br) — a verificação desse domínio no painel do Resend
(apontando o DNS pelo Cloudflare: registros MX + TXT/SPF + TXT/DKIM) é um
passo manual do usuário, feito fora deste código — **concluído em
23/09/2026** ("Próximos passos" item 4).

- `auth/ResendProperties.java`, `EmailConfiguration.java`,
  `ResendEmailSender.java`, `EmailException.java`.
- `ResendEmailSender implements EmailSender`, chamando
  `POST https://api.resend.com/emails` via `RestClient` (mesmo padrão de
  `SupabaseStorageService`/`TurnstileService` — sem o SDK dedicado
  `resend-java`, que o usuário cogitou mas foi descartado por já bastar uma
  chamada HTTP direta).
- `LoggingEmailSender` (stub da Fase 5) continua existindo, agora atrás de
  `@ConditionalOnProperty(servire.email.provider=log, matchIfMissing=true)`
  — é o bean padrão em dev/test, então rodar a aplicação localmente ou os
  testes de integração nunca dispara e-mail de verdade sem trocar
  `EMAIL_PROVIDER=resend` explicitamente.
- `application-prod.yml` passa a usar `servire.email.provider: resend` por
  padrão (override-ável via `EMAIL_PROVIDER`, ex. para voltar a "log" numa
  indisponibilidade do Resend). `RESEND_API_KEY` é obrigatório em produção
  (sem valor padrão, mesma regra de `JWT_SECRET`/`DB_URL`). `from` tem
  valor padrão `onboarding@resend.dev` (domínio de teste do próprio
  Resend, só entrega para o e-mail dono da conta) até `RESEND_FROM` ser
  trocado para um endereço `@servirea.com.br` depois da verificação do
  domínio estar pronta.

> ⚠️ **Segurança operacional:** a API key do Resend que o usuário colou no
> chat desta sessão (`re_WUReeCvS_...`) **não foi gravada em nenhum
> arquivo do repositório** — só é referenciada aqui como
> `${RESEND_API_KEY}` (variável de ambiente). Como essa chave passou por
> uma conversa de chat, o ideal é o usuário revogá-la e gerar uma nova no
> painel do Resend antes de configurar `RESEND_API_KEY` de verdade em
> produção (rotação de segredo que passou por um canal não pensado para
> segredos — mesma prudência da seção 90 do plano mestre, aplicada aqui
> por analogia).

**Riscos residuais:**
- Formato exato da API do Resend **não confirmado** contra uma chamada
  real de dentro deste ambiente de pesquisa (sem acesso de rede ao Resend
  aqui) — mesma ressalva já feita para Supabase Storage/Turnstile nas
  Fases 7/8.
- ~~Verificação do domínio `servirea.com.br` no painel do Resend ainda não
  fechada~~ — **fechada em 23/09/2026**, envio com `contato@servirea.com.br`
  confirmado ("Próximos passos" item 4).

## ⏳ Backoffice — API do operador do SaaS (seção 111 do plano mestre)

Implementado em 23/09/2026. **Não confundir** com a "Fase 11" deste
README (permissões/faltas/auditoria/e-mail). Aqui é o painel do **dono
da plataforma**, login em `admin.servirea.com.br`. Telas Angular ficam
fora deste repositório.

Desenho fechado com o usuário: operador `suporte@servirea.com.br`
(`usuario.operador_saas`), JWT `purpose=backoffice` **sem tenant**;
"entrar na paróquia" emite access token `suporte=true` (as rotas que já
existem de voluntário/escala). Padre **não** acessa `/admin/**`.
Voluntários **não** são CRUD no backoffice.

- Pacote `backoffice/`, migrations `V027` (operador, contato/endereço da paróquia, `backoffice_log`) e `V028` (`vigencia_ate`, `tipo_email`).
- `POST /admin/auth/login` (cookie de refresh em `/admin/auth`).
- `GET /admin/dashboard`; CRUD `/admin/paroquias` (filtros: situação
  ativos/inadimplentes/inativos, nome, CNPJ, e-mail, tipo de e-mail,
  contratadoDe/Ate, vigenciaDe/Ate; criar + primeiro admin, bloquear,
  desbloquear, suporte). O "marcar pago" original saiu na V029 (abaixo).
- CRUD `/admin/usuarios` + `PUT /admin/usuarios/{id}/paroquias`.
- `GET /admin/logs` (`backoffice_log`, tabela global).
- Seed do operador em profile `dev` via
  `servire.backoffice.operador-email` / `operador-senha` em
  `application-dev-local.yml` (não versionar senha).

✅ Confirmado nesta máquina em 23/09/2026: `mvn test` com
`BUILD SUCCESS`, `Tests run: 161, Failures: 0, Errors: 0` (era 140
depois da config do tenant; o backoffice soma os testes novos de
`backoffice/*`, JWT/filtro de suporte e `@PreAuthorize` de
`PERM_BACKOFFICE`).

### Financeiro manual (V029, 23/09/2026 — início da seção 112)

O "marcar como pago" só gravava `tenant.ultimo_pagamento_em`, sem
histórico. Agora é um financeiro de verdade, ainda **sem gateway**
(PIX manual, seção 131.3). Pacote `billing/`, migration `V029`
(`plano`, `preco_plano`, `assinatura`, `cobranca` — tabelas globais,
com RLS ligado sem policy).

Decisões com o usuário:
- **Catálogo + ajuste:** Standard/Pro semeados **sem preço** (valores
  ainda não definidos). O operador cadastra o preço com data de início
  em `/admin/planos`. A assinatura copia o valor vigente ou aceita um
  valor negociado. Reajuste nunca altera contrato antigo (seção 63).
- **Cobrança por período:** mensal = uma por mês de calendário; anual =
  uma a cada 12 meses. Geradas até "hoje + 1 mês" ao abrir o financeiro
  e por um job diário (03:00, Brasília). Idempotente pelo unique
  `(assinatura_id, competencia_inicio)`.
- **Pagamento:** uma ou várias cobranças de uma vez, com data, forma
  (PIX, cartão de crédito/débito, dinheiro, transferência, boleto,
  outro), valor pago e observação. Atualiza `ultimo_pagamento_em`,
  estende `vigencia_ate` e tira TRIAL/BLOQUEADO para ATIVO **só se não
  sobrar nada vencido**. Estornar e isentar existem para corrigir.
- **Atraso só é sinalizado:** listagem (`emAtraso=true`, colunas plano
  e dias de atraso) e dashboard (`emAtraso`, `recebidoNoMes`). O
  bloqueio automático após 3 dias (131.3) **não** foi feito; bloquear
  continua manual.

Rotas (todas `PERM_BACKOFFICE`): `GET/POST /admin/planos`,
`PUT /admin/planos/{id}`, `POST /admin/planos/{id}/precos`,
`GET /admin/paroquias/{id}/financeiro`,
`POST /admin/paroquias/{id}/assinatura` (e `/assinatura/cancelar`),
`POST /admin/paroquias/{id}/cobrancas/gerar`,
`POST /admin/paroquias/{id}/pagamentos`,
`POST /admin/paroquias/{id}/cobrancas/{cid}/estornar` e `/isentar`.
`POST /admin/paroquias/{id}/marcar-pago` **foi removido**.

✅ Confirmado nesta máquina em 23/09/2026: `mvn verify` com
`BUILD SUCCESS`, `Tests run: 183, Failures: 0, Errors: 0` (19 novos em
`BillingServiceIntegrationTest`, 2 em `MethodSecurityIntegrationTest`).
Também foi testado de ponta a ponta contra o `servire-dev-db` (login do
operador, preço, assinatura, pagamento de dois meses, filtro "em
atraso", dashboard e `backoffice_log`).

## Onde está o código (disco + GitHub) — 23/09/2026

Dois repositórios **irmãos** (não é monorepo). A pasta `servire` existe
**só no PC** — não vira um terceiro git.

```
Documents/servire/
  servire-api-back/     ← este repo (Java / Spring)
  servire-api-front/    ← Angular (outro git)
```

No GitHub (privados, `GustavoToebe`):

- https://github.com/GustavoToebe/servire-api-back
- https://github.com/GustavoToebe/servire-api-front

O que mudou nesta máquina em 23/09/2026:

- `Documents\servire-api` passou a `Documents\servire\servire-api-back`.
- O repo GitHub `GustavoToebe/servire-api` foi **renomeado** para
  `servire-api-back`. A URL antiga redireciona.
- O front era `Documents\paroquia-escalas-completo` e **não era git**.
  Virou `Documents\servire\servire-api-front`, `git init`, commit inicial,
  repo privado criado e enviado.
- GitHub CLI (`gh`) instalado com `winget install --id GitHub.cli -e` e
  autenticado como `GustavoToebe` (`gh auth login` no navegador).

O artifact Maven continua `br.com.servire:servire-api`. O `package.json`
do front continua `paroquia-escalas-front`. Só os nomes de pasta/repo
mudaram.

### Clonar em casa

```powershell
New-Item -ItemType Directory -Path "$HOME\Documents\servire" -Force
Set-Location "$HOME\Documents\servire"
git clone https://github.com/GustavoToebe/servire-api-back.git
git clone https://github.com/GustavoToebe/servire-api-front.git
```

Se já existir um clone antigo chamado `servire-api`:

```powershell
git remote set-url origin https://github.com/GustavoToebe/servire-api-back.git
```

`gh` em casa (opcional, para criar/renomear repo):

```powershell
winget install --id GitHub.cli -e
gh auth login
```

No Cursor: abrir `servire-api-back`, ou a pasta `servire` inteira se
quiser back e front no mesmo workspace. `application-dev-local.yml` **não
vai no git** — copiar/recriar em casa (gitignorado).

## Como rodar localmente

Requer um PostgreSQL acessível (local, Docker, ou outro) para o profile
`dev` — nunca aponte o profile `dev` para o Supabase de produção.

```bash
cp .env.example .env
# edite .env com a URL do seu Postgres de desenvolvimento
export $(cat .env | xargs)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

No Windows/PowerShell, o equivalente é (não existe `export $(cat ...)` no
PowerShell — configure cada variável com `$env:NOME = "valor"`, só válido
na sessão atual do terminal):

```powershell
mvn spring-boot:run -DskipTests "-Dspring-boot.run.profiles=dev"
```

> ⚠️ **Descoberto em 22/09/2026 (primeira vez que `spring-boot:run` foi
> executado de verdade neste projeto — até então só `mvn clean verify`,
> que usa Testcontainers):** rodar isso contra um PostgreSQL "limpo"
> qualquer (ex.: um container Docker novo) FALHA no Flyway, na migration
> `V005__table_escalas.sql`, com `ERROR: schema "auth" does not exist`.
> **Não é bug de produção** — é uma dependência externa documentada desde
> a Fase 1 (`auth.users`/`auth.uid()`, `storage.buckets`/`storage.objects`
> e os roles `anon`/`authenticated`/`service_role` só existem de verdade
> num projeto Supabase, nunca num Postgres "limpo"). O que estava
> **desatualizado** era o comentário nas migrations `V005`/`V013`/`V015`,
> apontando para um caminho que nunca existiu (`local-dev/00-supabase-stubs.sql`)
> em vez do caminho real do stub — corrigido nesta mesma rodada.
>
> Se você for rodar `spring-boot:run` (não só `mvn clean verify`) contra
> um Postgres seu (Docker, local, etc.) que não seja o Supabase de
> produção, suba o banco e aplique o stub ANTES da primeira execução:
>
> ```powershell
> # 1) Sobe um Postgres local descartável (só precisa rodar uma vez)
> docker run --name servire-dev-db -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=servire_dev -p 5432:5432 -d postgres:16
>
> # 2) Aplica o stub que simula o mínimo do Supabase (auth.users, storage.*,
> #    os três roles) — mesmo arquivo usado pelos testes de integração via
> #    Testcontainers (AbstractIntegrationTest), aplicado aqui manualmente
> #    porque spring-boot:run não passa por Testcontainers
> Get-Content src\test\resources\testcontainers\supabase-stubs.sql | docker exec -i servire-dev-db psql -U postgres -d servire_dev
>
> # 3) Só agora rodar a aplicação
> mvn spring-boot:run -DskipTests "-Dspring-boot.run.profiles=dev"
> ```
>
> Operador do backoffice em dev: em `application-dev-local.yml` (gitignorado):
>
> ```yaml
> servire:
>   backoffice:
>     operador-email: suporte@servirea.com.br
>     operador-senha: uma-senha-sua
> ```
>
> Sem isso o seed `DevOperadorSeed` não roda — crie o usuário na mão ou
> deixe o teste criar o dele.
>
> Se o Flyway já tiver tentado migrar e falhado antes de você aplicar o
> stub (como aconteceu nesta descoberta), não tem problema — o Flyway já
> desfaz a migration que falhou (`Changes successfully rolled back`) e
> retoma exatamente dali na próxima tentativa.

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
    Permissao.java, RolePermissoes.java   <- Fase 11
  auth/
    Usuario.java, UsuarioRepository.java
    UsuarioTenant.java, UsuarioTenantId.java, UsuarioTenantRepository.java
    RefreshToken.java, RefreshTokenRepository.java, RefreshTokenService.java
    PasswordResetToken.java, PasswordResetTokenRepository.java, PasswordResetTokenService.java
    EmailSender.java, LoggingEmailSender.java   <- stub (bean padrão dev/test desde a Fase 11)
    ResendProperties.java, EmailConfiguration.java, ResendEmailSender.java, EmailException.java   <- Fase 11
    AuthService.java, AuthController.java
    dto/   <- LoginRequest, LoginResponse, SelectTenantRequest, RefreshRequest, AccessTokenResponse, ForgotPasswordRequest, ResetPasswordRequest, TenantResumo
  voluntario/
    Voluntario.java, VoluntarioRepository.java, VoluntarioService.java, VoluntarioController.java
    Responsavel.java, ResponsavelRepository.java
    TipoVoluntario.java, FuncaoEscala.java
    Periodo.java, DisponibilidadeVoluntario.java, DisponibilidadeVoluntarioRepository.java,
    DisponibilidadeVoluntarioService.java, DisponibilidadeVoluntarioController.java   <- Fase 11
    dto/   <- VoluntarioRequest, VoluntarioResponse, ResponsavelRequest, ResponsavelResponse, FotoUrlResponse,
              DisponibilidadeVoluntarioRequest, DisponibilidadeVoluntarioResponse (Fase 11)
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
    Escala.java, EscalaEvento.java, EscalaVaga.java, EscalaRepository.java, EscalaVagaRepository.java (Fase 11)
    TipoEscala.java, StatusEscala.java, Presenca.java (Fase 11), EscalaService.java, EscalaController.java
    dto/   <- EscalaRequest, EscalaEventoRequest, EscalaVagaRequest, EscalaResponse, EscalaEventoResponse,
              EscalaVagaResponse, PresencaRequest (Fase 11)
  audit/   <- Fase 11 (seção 59)
    AuditLog.java, AuditLogRepository.java, AuditLogService.java, AuditLogController.java
    dto/   <- AuditLogResponse
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
    V024.sql        <- escala_vagas.presenca (Fase 11, controle de faltas)
    V025.sql        <- disponibilidade_voluntario (Fase 11)
    V026.sql        <- audit_log (Fase 11)
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
pacotes `storage/`, `inscricao/` e `escala/` entraram na Fase 7/8/9; o
pacote `audit/` (nome próprio deste projeto para a `auditoria/` da
estrutura-alvo, seção 16) entrou na Fase 11. Ainda não existem `config/`,
`backoffice/`, `arquivo/`, `billing/` da estrutura-alvo completa.

## Próximos passos

> **Atualização (22/09/2026):** os itens antigos 2 ("roles/permissões de
> verdade"), 3 ("decidir provedor de e-mail"), 4 ("desenhar faltas e
> disponibilidade") e 6 ("tabela de auditoria dedicada") desta lista foram
> **implementados na Fase 11** (ver seção própria acima) — por pedido
> explícito do usuário, SEM testes automatizados novos ainda (decisão
> dele: construir as 5 funcionalidades primeiro, testar depois, mesmo
> padrão já usado nas Fases 6-9). A Fase 11 já tem um `mvn clean verify`
> real com `BUILD SUCCESS` (75 testes, 0 falhas, 0 erros), mas isso só
> confirmou schema/contexto Spring/efeito colateral.
>
> **Atualização (22/09/2026, à tarde):** os quatro débitos de teste do
> item 1 abaixo foram pagos nesta rodada — `MethodSecurityIntegrationTest`
> (novo, pacote `security/`), quatro testes novos em
> `EscalaServiceIntegrationTest` para `registrarPresenca`,
> `DisponibilidadeVoluntarioServiceIntegrationTest` (novo) e
> `ResendEmailSenderTest` (novo). Essa rodada revelou (e uma rodada
> seguinte corrigiu) os Bugs reais #12, #13 e #14 — ver seção "Fase 11"
> acima.
>
> ✅ **Atualização (22/09/2026): item 1 abaixo está CONFIRMADO.** Quinta
> rodada de `mvn clean verify` real: `BUILD SUCCESS`, `Tests run: 104,
> Failures: 0, Errors: 0`. Os quatro débitos de teste da Fase 11 e os
> fixes dos Bugs reais #12/#13/#14 estão todos confirmados de verdade.

1. ✅ **Testes escritos em 22/09/2026 — CONFIRMADOS pela quinta rodada de
   `mvn clean verify` real (`BUILD SUCCESS`, 104 testes, 0 falhas, 0
   erros):**
   - `security/MethodSecurityIntegrationTest` (novo) — confirma
     `@PreAuthorize`/`@EnableMethodSecurity` via `MockMvc` de verdade: 401
     sem autenticação, 403 autenticado mas sem a `PERM_*`/role certa, 200
     quando a permissão bate, e a distinção `hasAuthority("PERM_...")` vs
     `hasRole("ADMIN")` (`AuditLogController`). Os serviços por trás de
     cada controller são substituídos por `@MockitoBean` — o teste isola a
     checagem de autorização em si, não repete regra de negócio já testada
     em outro lugar. Achado registrado no javadoc da classe (não um bug):
     a validação `@Valid` do corpo roda ANTES do `@PreAuthorize` (resolução
     de argumentos do método acontece antes do proxy de segurança), então
     todo corpo JSON usado no teste precisa ser válido — um corpo inválido
     mascararia o que o teste quer medir com um 400 em vez de um 403.
   - `EscalaServiceIntegrationTest` — quatro testes novos para
     `registrarPresenca` (Fase 11, seção 131.5 item 11): atualiza a
     presença de verdade (com leitura de volta do repositório), rejeita
     vaga sem voluntário alocado, rejeita escala `CANCELADA`, e vaga
     inexistente vira 404.
   - `voluntario/DisponibilidadeVoluntarioServiceIntegrationTest` (novo) —
     cobre `criar` (recorrente por dia da semana, pontual por data, a
     regra XOR nos dois sentidos, voluntário inexistente, e a duplicata que
     o índice único parcial `ux_disponibilidade_recorrente`/V025 rejeita),
     `listar` (isolado por voluntário) e `excluir` (incluindo o 404
     deliberado ao tentar excluir a disponibilidade de OUTRO voluntário —
     nunca revelar que ela existe).
   - `auth/ResendEmailSenderTest` (novo) — mesmo padrão de
     `TurnstileServiceTest`/`SupabaseStorageServiceTest`
     (`MockRestServiceServer`, sem subir o Spring context): confirma o
     formato exato do request (endpoint, header `Authorization: Bearer`,
     corpo `{"from","to","subject","html"}`), o fail-closed de
     `requireConfigurado` (`apiKey`/`from` ausentes) e que qualquer falha
     HTTP (500, 401) vira `EmailException` — nunca engolida. Continua sem
     confirmação contra a API real do Resend (nenhum acesso de rede a
     serviços externos a partir deste ambiente de pesquisa).
2. ✅ **Confirmado em 22/09/2026 18:50** — `mvn clean verify` real:
   `BUILD SUCCESS`, `Tests run: 109, Failures: 0, Errors: 0` (era 104
   na quinta rodada da Fase 11; os 5 a mais desta sessão incluem o
   teste abaixo, o trim da service_role key e os HTTP do
   `JwtAuthenticationFilter`). 
   `InscricaoServiceIntegrationTest.atualizarPendenteTrocandoOResponsavelPrincipalNaoLancaConflictException`
   troca o responsável principal de uma inscrição PENDENTE via
   `atualizarPendente` e confirma que isso não lança `ConflictException`
   — fecha o fix proativo em `InscricaoService.substituirResponsaveis`
   (mesmo padrão do Bug real #10). Também passam os testes novos do
   Storage (`apikey`, trim da key, `VoluntarioResponse.de` fora da
   sessão).
3. Confirmar de verdade contra um serviço real: o formato da API REST do
   Supabase Storage (Fase 7), a resposta do Cloudflare Turnstile (Fase 8)
   e a API do Resend (Fase 11) — **os três fechados em 22/09/2026**
   (Resend com o domínio próprio `contato@servirea.com.br` confirmado em
   23/09/2026 — ver item 4).

   ✅ **Turnstile — CONFIRMADO de verdade em 22/09/2026, primeira das três
   integrações externas a ser validada ponta a ponta.** Widget "Servirea"
   criado no painel da Cloudflare (hostnames `servirea.com.br` e
   `localhost`, modo "Managed"); o usuário gerou um token real numa
   página HTML avulsa local com o site key, e submeteu
   `POST /public/{tenantSlug}/inscricoes` (rodando localmente via
   `mvn spring-boot:run -Dspring-boot.run.profiles=dev`, contra um
   Postgres Docker local com o stub de `auth`/`storage` aplicado — ver
   "Como rodar localmente") com esse token via `curl.exe`. Resultado:
   `TurnstileService` chamou o `siteverify` real da Cloudflare, recebeu
   `success: true` e a inscrição foi criada normalmente (`status:
   PENDENTE`, responsável gravado) — confirma que o formato da resposta
   real do Cloudflare bate com o que o `Map<String,Object>`/campo
   `success` do `TurnstileService` espera (mesma suposição que já era só
   documentação até aqui). Numa tentativa anterior na mesma sessão, a
   mesma chamada falhou com `RestClientException` (fail-closed correto:
   400 "Não foi possível validar a verificação anti-robô agora") — não
   foi possível confirmar a causa raiz exata (rede/proxy/antivírus da
   máquina do usuário, provavelmente transitória) antes de funcionar
   normalmente na tentativa seguinte; se esse erro voltar a acontecer de
   forma recorrente, investigar o stack trace completo do `WARN` logado
   por `TurnstileService.validar`. **Não confirmado ainda:** o caminho de
   um token inválido/expirado contra a API real (o `TurnstileServiceTest`
   unitário já cobre esse caminho, mas só contra um mock).
   **Nota de segurança:** a secret key desse widget apareceu num print
   compartilhado nesta conversa antes de o widget ter hostname
   configurado — recomendado rotacionar a secret key pelo botão "Rotate
   Secret Key" do próprio painel (limite: uma rotação a cada 2h, chave
   antiga continua válida durante a transição) antes de usar em produção.

   ✅ **Supabase Storage — CONFIRMADO de verdade em 22/09/2026, segunda
   das três integrações externas.** Projeto `qcybebkhwhrudbwoweip`,
   bucket privado `voluntarios-fotos`. Upload REST + URL assinada +
   download no navegador com `?token=` funcionaram; o Kong exige
   `Authorization` **e** `apikey` (Bug real #16). Detalhe, bloqueios de
   config e o 500 do POST /foto depois do upload (Bug real #17) estão
   na seção da Fase 7. **Não exercitado:** `DELETE` real. **Rotacionar**
   a `service_role` que vazou no chat antes de produção.

   ✅ **Resend — CONFIRMADO de verdade em 22/09/2026, terceira e última
   das três integrações externas.** `POST /auth/forgot-password` com
   profile `dev`, `servire.email.provider=resend` em
   `application-dev-local.yml`, remetente `onboarding@resend.dev` (o
   domínio `servirea.com.br` ainda estava Pending/Checking DNS no
   painel). A API respondeu 202; o Resend entregou no Gmail do dono da
   conta (`gustavotoebe4@gmail.com`) o e-mail "Redefinição de senha —
   Servire" com o link de reset. Confirma o `POST https://api.resend.com/emails`
   (`Authorization: Bearer`, corpo `from`/`to`/`subject`/`html`).
   `${EMAIL_PROVIDER:log}` em `application-dev.yml` **sobrescrevia** o
   arquivo local e mantinha o `LoggingEmailSender` (mesmo padrão do
   Storage) — removido. Dois `servire:` no YAML local também apagavam
   o bloco de cima; tem que ser um único `servire:` com `storage` +
   `email`. Envio com `contato@servirea.com.br` confirmado em 23/09/2026
   (item 4). **Rotacionar** a API key `re_` que vazou no chat.
4. ✅ **Domínio próprio no Resend — CONFIRMADO em 23/09/2026.**
   `servirea.com.br` ficou "Verified" no Resend (região São Paulo,
   `sa-east-1`) em 23/09/2026 00:25. `POST /auth/forgot-password` com
   profile `dev`, `provider: resend` e `from: contato@servirea.com.br` em
   `application-dev-local.yml` devolveu 202, e o e-mail chegou no Gmail
   do dono da conta em 1 segundo com **SPF, DKIM e DMARC em PASS**
   (Return-Path `rsend.servirea.com.br`, DKIM `d=servirea.com.br
   s=resend`, envio feito via Amazon SES `sa-east-1`). Passou inclusive
   com o DMARC ainda em `p=reject`.
   DNS no Cloudflare, estado final:
   - DKIM `resend._domainkey` TXT e CNAMEs `send`/`rsend` → `*.forge.rmta.net`
     (criados pelo Resend; o SPF do envio é verificado no `rsend`, não na raiz).
   - SPF da raiz `v=spf1 ~all`: nada além do Resend envia como
     `@servirea.com.br`. **Não** precisa de `include:resend.com`, apesar
     da sugestão do assistente do Cloudflare.
   - DMARC `_dmarc`: `v=DMARC1; p=none; rua=mailto:...@dmarc-reports.cloudflare.net`
     (relatórios no DMARC Management do Cloudflare). Começa em `none` de
     propósito; subir para `p=quarantine` depois de 2 a 4 semanas de
     relatórios limpos.
   - MX nulo (`.`) na raiz: `contato@servirea.com.br` **não recebe**
     e-mail (respostas voltam com erro). Para receber, ativar o Email
     Routing do Cloudflare encaminhando para o Gmail.
   - Configuração do domínio no Resend: tracking de abertura/clique
     **desligado** (reescreveria o link de reset) e TLS "Opportunistic".
   O "Warning" do Cloudflare no DKIM (chave de 1024 bits do Resend) e o
   "Fail" do BIMI (exige DMARC `quarantine`/`reject` + certificado pago)
   são esperados.
5. ✅ **Picker de candidatos (seção 49) — CONFIRMADO em 22/09/2026 19:13**
   (`BUILD SUCCESS`, 117 testes). `GET /escalas/{eventoId}/candidatos?funcao=`
   + filtro de `DisponibilidadeVoluntario` (ver seção da Fase 9).
5b. ✅ **Compromissos do voluntário — CONFIRMADO em 22/09/2026 19:20**
   (`BUILD SUCCESS`, 122 testes). `GET /voluntarios/{id}/commitments`
   (ver seção da Fase 6 / Fase 9).
5c. ✅ **Alocação pontual da vaga — CONFIRMADO em 22/09/2026 19:25**
   (`BUILD SUCCESS`, 133 testes). `PATCH /escalas/vagas/{vagaId}`
   (ver seção da Fase 9).
5d. ✅ **Configuração do tenant — CONFIRMADO em 22/09/2026 19:31**
   (`BUILD SUCCESS`, 140 testes). `GET`/`PUT /tenant`
   (`PERM_CONFIG_WRITE`, ver Fase 11).
6. Validar a configuração real do reverse proxy do VPS de produção
   (Nginx/Caddy, seção 11/89) quanto a `X-Forwarded-For` — o rate limit da
   Fase 8 depende de esse header vir reescrito pelo proxy, não só
   repassado do cliente.
7. **Segurança operacional:** revogar/rotacionar a API key do Resend que
   foi colada no chat desta sessão (`re_WUReeCvS_...`) e gerar uma nova
   antes de configurar `RESEND_API_KEY` em produção — ver aviso na seção
   da Fase 11 acima.
8. ✅ **API do backoffice (seção 111) — implementada em 23/09/2026.**
   Pacote `backoffice/`, migrations V027/V028, JWT `purpose=backoffice` e
   sessão de suporte (`suporte=true`). Endpoints `/admin/**`. Telas
   Angular (`admin.servirea.com.br` e faixa "modo suporte" no app da
   paróquia) ficam no front. Confirmado nesta máquina: `mvn test`
   `BUILD SUCCESS`, 161 testes, 0 falhas.

   Billing (seção 112) começou em 23/09/2026 com o financeiro manual
   (V029, ver seção "Financeiro manual" acima); faltam gateway,
   webhooks e bloqueio automático por atraso.

   Próximo de código no plano depois desta API: resto do Billing (seção 112),
   Deploy (113), migração de dados (114) e remoção do Supabase direto
   do Angular (115).
