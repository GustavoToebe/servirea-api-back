# PLANO MESTRE DE EVOLUÇÃO — Servire — SaaS de Gestão Paroquial Multi-Tenant

**Versão:** 2.0 — MVP de baixo custo  
**Data de consolidação:** 21/09/2026  
**Status:** Arquitetura alvo revisada e decisões consolidadas — 18 decisões de produto da seção 122 respondidas em 21/09/2026 (ver seção 131). Fase 0 e Fase 1 (reconstrução do schema real + baseline Flyway versionado e validado) concluídas em 21/09/2026 (ver seções 100, 101, 124 e o `servire-database-baseline.zip` entregue ao usuário). Fase 2 (fundação Spring Boot 4.1.1) concluída e **com build verificado de verdade** em 21/09/2026 — `mvn clean verify` real no ambiente do usuário terminou em `BUILD SUCCESS`, 11 testes, 0 falhas, repositório sincronizado em `https://github.com/GustavoToebe/servire-api` (ver seção 102). Fase 3 (modelo SaaS lógico: `tenant`/`usuario`/`usuario_tenant`/`refresh_token`) e Fase 4 (`TenantContext`/`@TenantId`/testes de isolamento) implementadas e **concluídas com build verificado de verdade** em 21/09/2026, com JPA completo só para `tenant`/`usuario` e a entidade `Voluntario` como primeiro vetor tenant-aware (ver seções 103 e 104). Rodando `mvn clean verify` de verdade nesta fase, o usuário encontrou e ajudou a confirmar a correção de **três bugs reais**, todos documentados na seção 104: (1) o resolver de tenant derrubava a inicialização inteira do Spring; (2) contagem de migrations desatualizada no teste (15→21); (3) o "singleton container" do Testcontainers não era de fato singleton, causando falhas de conexão intermitentes em `TenantIsolationIntegrationTest`. **Os três já estão confirmados: `mvn clean verify` final terminou em `BUILD SUCCESS`, 13 testes, 0 falhas, 0 erros** — nenhuma ressalva pendente nesta fase. Fase 5 (autenticação própria, seção 105) **implementada e concluída com build verificado de verdade** em 21/09/2026 — código completo escrito (login, JWT, refresh com rotation, seleção de tenant, logout, forgot/reset password, `SecurityConfig`/`JwtAuthenticationFilter` substituindo o `DevFixedTenantFilter` da Fase 4), sincronizado no ambiente do usuário. Rodando `mvn clean verify` de verdade nesta fase, o usuário encontrou e ajudou a confirmar a correção de **dois bugs reais**, documentados na seção 105: (4) `jackson-databind` sumindo da compilação por mediação de dependências do Maven; (5) `ObjectMapper` do Jackson 2 nunca registrado como bean pelo Spring Boot 4 (que usa Jackson 3 por padrão). **Confirmação final recebida: `mvn clean verify` terminou em `BUILD SUCCESS`, 13 testes, 0 falhas, 0 erros** (21/09/2026, 23:37, reconfirmado 22/09/2026 08:14) — nenhuma ressalva de compilação/contexto pendente. Fase 6 (voluntários/responsáveis, seção 106) **implementada e com build real confirmado em 22/09/2026** — a pedido explícito do usuário, que decidiu priorizar velocidade e adiar tanto os testes pendentes da Fase 5 (Task 17) quanto os novos da Fase 6 (seção 80). `mvn clean verify` terminou em `BUILD SUCCESS`, 13 testes, 0 falhas, 0 erros (08:36) — confirma inclusive que o maior risco técnico da fase, o mapeamento de `funcoesHabilitadas` (array de ENUM nativo do Postgres) via Hibernate, funciona de verdade contra Hibernate 7.4.5.Final + Postgres real (ver seção 106). Ressalva que continua: nenhum teste automatizado cobre as regras de negócio novas (responsável principal único, substituição de responsáveis). Durante o planejamento das Fases 7/8/9, uma releitura completa de `V021` revelou que `Responsavel` tinha sido escrita sem `@TenantId` (bug real encontrado por revisão de schema, não por build — corrigido antes de qualquer execução real acusar o problema; ver seção 106). Fases 7 (Storage, seção 107), 8 (Inscrições públicas, seção 108) e 9 (Escalas, seção 109) foram **implementadas em 22/09/2026, numa única rodada conjunta a pedido explícito do usuário** ("Pode fazer a 7, 8 e 9 de uma vez e depois rodamos o mvn clean verify para ver o build"). Nova migration `V023` repontou as FKs `escalas.created_by`/`inscricoes.aprovado_por`/`inscricoes.rejeitado_por` de `auth.users` (Supabase Auth) para `public.usuario`, e somou `escalas.version` para o controle otimista da seção 47. **Primeira rodada de `mvn clean verify` real (22/09/2026, 09:14-09:16): `BUILD FAILURE`, 13 testes, 0 falhas, 8 erros — Bug real #6**, confirmado pelo log completo enviado pelo usuário: faltava o bean `RestClient.Builder`, injetado por `SupabaseStorageService` (Fase 7) e `TurnstileService` (Fase 8), derrubando o contexto Spring inteiro em todo teste de integração. Corrigido com a nova classe `br.com.servire.api.web.RestClientConfiguration`, declarando o bean manualmente em vez de depender de auto-configuration. **Segunda rodada de `mvn clean verify` real (22/09/2026, 09:24) confirmou: `BUILD SUCCESS`, 13 testes, 0 falhas, 0 erros — Bug real #6 está corrigido de verdade.** Isso confirma que o contexto Spring sobe com todas as entidades/beans novos das Fases 7/8/9 e que os 13 testes existentes continuam passando; não confirma o comportamento funcional das três fases em si, já que nenhum teste novo foi escrito para elas (débito técnico assumido). Ver seções 107, 108 e 109 para o detalhamento completo de cada fase e os demais riscos residuais ainda não confirmados (formato da API REST do Supabase Storage, resposta do Cloudflare Turnstile, confiabilidade de `X-Forwarded-For`, e o controle otimista/cascata tenant-aware de três níveis das escalas). **Fase 10 (multi-tenant real, seção 110) implementada em 22/09/2026, junto com TODO o débito de testes automatizados pendente das Fases 5-9 — a mesma rodada em que o usuário reverteu a decisão anterior de adiar testes ("vamos fazer mais a Fase 10 aí fazemos todos os testes pendentes")**, ainda **SEM confirmação de `mvn clean verify` real** (nenhum `mvn` disponível neste ambiente de pesquisa — Maven Central bloqueado; todo o código novo foi revisado manualmente e teve contagens de argumento de construtor/record checadas por um script auxiliar, que já pegou e ajudou a corrigir três bugs reais de contagem de argumento antes de qualquer build). Escrevendo `SupabaseStorageServiceTest` (débito de testes da Fase 7), foi encontrado o **Bug real #7**: as três chamadas HTTP de `SupabaseStorageService` codificavam a barra (`/`) do caminho do arquivo como `%2F` na URL, o que teria quebrado toda chamada real ao Supabase Storage — corrigido, também ainda sem confirmação de build real. **Primeira rodada de `mvn clean verify` real da Fase 10 (22/09/2026): `BUILD FAILURE`, `Tests run: 75, Failures: 2, Errors: 14`.** Analisado o log completo enviado pelo usuário, foram encontrados e corrigidos quatro problemas: duas correções de teste (`VoluntarioServiceIntegrationTest`/`EscalaServiceIntegrationTest` gerando `codigo` de tenant fixo em vez de randomizado, colidindo com a `UNIQUE constraint`; `InscricaoRateLimiterTest` usando `Duration.ofNanos(1)` para simular janela expirada, uma suposição de resolução de relógio que não se confirmou na máquina Windows do usuário) e **dois bugs reais de produção**: **Bug real #8** — `InscricaoService.criarPublica` tinha `@Transactional` no método inteiro, mas só chamava `TenantContext.set(...)` dentro do corpo; como o Hibernate fixa o tenant da sessão na ABERTURA da sessão (que sob `@Transactional` declarativo acontece na entrada do método, antes do corpo rodar), toda gravação saía com o tenant sentinela `SEM_TENANT`, violando a FK `inscricoes_tenant_id_fkey` — a funcionalidade carro-chefe da Fase 8 nunca funcionou de fato contra um banco real; corrigido abrindo a transação manualmente via `TransactionTemplate`, depois de `TenantContext.set(...)` já ter rodado (seção 108). **Bug real #9** — a detecção de reuso de refresh token (seção 36) chamava o `UPDATE` de revogação em massa e, na mesma transação física, lançava `UnauthorizedException` (`RuntimeException`), cujo rollback padrão do Spring desfazia silenciosamente a revogação; corrigido anotando `RefreshTokenRepository.revogarTodosAtivosDoUsuario` com `@Transactional(propagation = Propagation.REQUIRES_NEW)`, forçando essa revogação a commitar numa transação própria e independente (seção 105). **Segunda rodada de `mvn clean verify` real, depois dessas quatro correções: `BUILD FAILURE`, `Tests run: 75, Failures: 0, Errors: 1`** — só sobrou um erro, exposto agora que a correção do `codigo` duplicado parou de mascarar os demais métodos de `VoluntarioServiceIntegrationTest`: **Bug real #10** — `VoluntarioService.substituirResponsaveis` chamava `voluntario.getResponsaveis().clear()` (agendando os `DELETE`s via `orphanRemoval=true`) mas não forçava a sincronização com o banco antes de inserir os novos responsáveis; o Hibernate podia enviar o `INSERT` do novo responsável principal ANTES do `DELETE` do antigo, violando o índice único parcial `uq_responsavel_principal_por_voluntario` (V004) enquanto os dois "principais" coexistiam por um instante na mesma transação. Corrigido com um `entityManager.flush()` explícito logo após o `clear()` (seção 106) — o usuário aplicou essa correção com ajuda de outra IA (sessão anterior já no limite de uso) e o mesmo bug foi corrigido proativamente por analogia em `InscricaoService.substituirResponsaveis` (seção 108), ainda sem teste automatizado que o exercite. **Terceira rodada de `mvn clean verify` real, depois do Bug real #10: `BUILD SUCCESS`, `Tests run: 75, Failures: 0, Errors: 0`** — toda a Fase 10 + o débito de testes automatizados das Fases 5-9 estão finalmente confirmados de verdade contra um Postgres real. Ver seção 110 para o detalhamento completo do que foi feito, e as seções 105/106/107/108/109 (atualizadas) para o débito de testes pago por fase e os três bugs reais novos (#8, #9, #10). **Fase 11 implementada em 22/09/2026, a pedido explícito do usuário ("Pode fazer todos os itens do 1 ao 5"), reunindo cinco funcionalidades: (1) roles/permissões reais (seção 31) via `Permissao`/`RolePermissoes` + `@PreAuthorize` nos controllers; (2) controle de faltas (seção 131.5 item 11) via coluna `presenca` em `escala_vagas` (V024); (3) disponibilidade do voluntário (seção 131.5 item 12) via nova entidade `disponibilidade_voluntario` (V025); (4) tabela de auditoria dedicada (seção 59) via novo pacote `audit/` (V026); (5) provedor de e-mail real Resend (`ResendEmailSender`), decidido com o usuário como Cloudflare (DNS) + Resend (envio) — domínio `servirea.com.br` (seção 122 item 16) confirmado REGISTRADO pelo usuário nesta mesma data.** Por decisão explícita do usuário, NENHUM teste automatizado novo foi escrito nesta rodada ("vamos desenvolver umas 4 ou 5 coisas e aí implementamos os testes" — mesmo padrão já usado nas Fases 6-9). **Primeira rodada de `mvn clean verify` real da Fase 11 (22/09/2026, 14:05): `BUILD FAILURE`, `Tests run: 75, Failures: 1, Errors: 0` — Bug real #11 (teste desatualizado, não produção): `FlywayMigrationIntegrationTest` tinha o total de migrations fixado em 23, sem contar as três novas desta fase (V024/V025/V026); corrigido para 26 (e a descrição da última migration para "table audit log").** Nenhum código de produção foi tocado por essa correção. **Segunda rodada de `mvn clean verify` real da Fase 11 (22/09/2026, 14:08): `BUILD SUCCESS`, `Tests run: 75, Failures: 0, Errors: 0`** — confirma o fix do Bug real #11. Precisão sobre o alcance dessa confirmação (nenhum teste novo foi escrito nesta rodada): fica confirmado, por efeito colateral dos testes já existentes, que os schemas/mapeamentos JPA novos (`presenca_vaga`, `periodo_dia`, o array `text[]` de `audit_log.changed_fields`) batem com o Postgres real (via `ddl-auto: validate`, que roda em toda subida de contexto), que o contexto Spring sobe corretamente com os dois beans concorrentes de `EmailSender` e com `@EnableMethodSecurity` ativo, e que `AuditLogService.registrar` executa sem erro de SQL quando chamado pelos serviços de Voluntário/Escala/Inscrição já testados. **Continua SEM confirmação**: a aplicação real do `@PreAuthorize` em nível HTTP, `EscalaService.registrarPresenca`, todo o fluxo de `DisponibilidadeVoluntario`, e `ResendEmailSender` (nunca instanciado nos testes, que forçam `provider: log`) — ver README.md, seção "Fase 11", para o detalhamento completo de cada item e os riscos residuais.

---

# Identidade do produto

## Nome oficial

```text
Servire
```

“Servire” vem do latim, ligado a **servir**, e representa bem a proposta do produto: organizar pessoas, ministérios, escalas e atividades paroquiais sem limitar a solução apenas ao contexto de coroinhas.

O nome foi escolhido para permitir a evolução natural do produto para outros contextos de serviço dentro da paróquia, como:

- coroinhas;
- acólitos;
- ministros;
- leitores;
- música;
- pastorais;
- equipes litúrgicas;
- eventos;
- outros ministérios e serviços paroquiais.

## Nomes dos produtos

```text
Servire
Servire Web
Servire API
Servire Admin
Servire Mobile
```

## Nomes técnicos sugeridos

```text
servire-web
servire-api
servire-admin
servire-mobile
```

## Posicionamento sugerido

```text
Servire
Gestão de escalas e ministérios paroquiais em um só lugar.
```

O nome **Servire** deverá ser utilizado como referência principal do produto em documentação, repositórios, artefatos técnicos, interfaces e comunicação futura.


---

# 0. Objetivo deste documento

Este documento substitui a versão anterior do Plano Mestre como direção arquitetural principal do projeto.

Ele consolida:

1. o estado técnico atual do sistema de escalas da Paróquia São José Operário;
2. as regras de negócio que não podem ser perdidas;
3. os riscos e dívidas técnicas já identificados;
4. a estratégia de migração do Supabase como backend direto para uma API Java;
5. a arquitetura MVP com custo operacional mínimo;
6. a estratégia multi-tenant baseada em um único PostgreSQL compartilhado;
7. autenticação própria com Spring Security + JWT;
8. armazenamento de arquivos no Supabase Storage;
9. o plano incremental de migração;
10. critérios de segurança, isolamento, testes e aceite;
11. os gatilhos que indicarão quando a infraestrutura gratuita deverá ser substituída;
12. instruções para que outra IA ou desenvolvedor continue o projeto sem perder contexto.

O objetivo principal não é simplesmente reescrever o sistema.

O objetivo é:

> preservar regras de negócio existentes, corrigir riscos de segurança e integridade e evoluir o sistema para um SaaS comercializável sem criar custos de infraestrutura prematuramente.

---

# 1. Decisões arquiteturais desta versão

As seguintes decisões passam a ser oficiais para o MVP.

## 1.1 Frontend

```text
Angular SPA
    ↓
Cloudflare Pages
```

O frontend Angular será hospedado no Cloudflare Pages.

Motivos:

- CDN global;
- HTTPS;
- integração com Git;
- build e deploy automáticos;
- custo inicial zero;
- não exige servidor próprio para o frontend.

Nenhum secret poderá existir no bundle Angular.

---

## 1.2 Backend

```text
Java 21
Spring Boot 3.x
Spring MVC
Hibernate 6
Docker
    ↓
VPS ICP CORE
```

O backend será executado inicialmente em um único VPS utilizando containers Docker.

Premissa comercial atual:

```text
VPS ICP CORE
Custo informado: aproximadamente R$ 29,90/mês
```

O valor do provedor deve ser confirmado no momento da contratação.

Nesta fase não serão utilizados:

- AWS ECS;
- ECR;
- ALB;
- RDS;
- CloudFront;
- Secrets Manager;
- RDS Proxy.

Esses componentes permanecem como possibilidades futuras, não como requisitos do MVP.

---

## 1.3 Banco de dados

Será utilizado:

```text
Supabase PostgreSQL
Plano Free
1 projeto principal do SaaS
```

A API Java acessará o PostgreSQL diretamente via JDBC.

O Angular deixará progressivamente de acessar as tabelas do Supabase diretamente.

---

## 1.4 Storage

Será utilizado:

```text
Supabase Storage
```

Nesta versão do MVP o AWS S3 deixa de ser requisito.

Arquivos privados continuarão sendo acessados somente após autorização do backend.

---

## 1.5 Multi-tenancy

A estratégia anterior:

```text
Database-per-Tenant
```

fica substituída no MVP por:

```text
Shared Database
+
Shared Schema
+
tenant_id por registro
```

Exemplo:

```text
PostgreSQL
   |
   +-- tenant
   +-- usuario
   +-- usuario_tenant
   +-- voluntario
   +-- responsavel
   +-- inscricao
   +-- escala
   +-- ...
```

Tabelas de domínio conterão:

```text
tenant_id
```

Não serão criados bancos:

```text
cli_1001
cli_1002
cli_1003
```

durante o MVP.

---

# 2. Correção importante sobre o Supabase Free

Na data desta revisão, a documentação oficial do Supabase informa que o plano Free permite até dois projetos ativos.

Mesmo assim, a arquitetura será deliberadamente desenhada para funcionar com:

```text
1 projeto PostgreSQL principal
```

Isso mantém o SaaS simples e permite preservar eventual segundo projeto Free para:

- homologação;
- testes;
- contingência temporária;
- experimentos de migração.

O sistema não deverá depender da existência desse segundo projeto.

---

# 3. Limites conhecidos da infraestrutura gratuita

A arquitetura de baixo custo possui limites e eles precisam ser tratados como parte do produto.

## 3.1 Supabase Database Free

Referência atual:

```text
500 MB de database size por projeto
```

Ao atingir o limite aplicável do Free, o projeto pode entrar em modo somente leitura.

Portanto deverão existir alertas antes desse ponto.

Gatilhos sugeridos:

```text
60% → atenção
70% → planejamento de upgrade
80% → upgrade recomendado antes de novos clientes
```

Não esperar chegar a 100%.

---

## 3.2 Supabase Storage Free

Referência atual:

```text
1 GB de Storage
```

Fotos devem ser otimizadas antes ou durante o upload.

Evitar guardar:

- imagens originais gigantes;
- múltiplas versões desnecessárias;
- arquivos sem referência;
- arquivos temporários eternamente.

---

## 3.3 Pausa por inatividade

Projetos Free do Supabase podem ser pausados após período de baixa atividade.

Esse comportamento é aceitável durante desenvolvimento e validação inicial.

Antes de assumir SLA comercial relevante, deve ser reavaliado.

---

## 3.4 Backups

O plano Free não deve ser tratado como solução completa de backup de produção.

Antes de operar com dados reais de múltiplos clientes, implementar backup externo.

Estratégia inicial possível:

```text
cron no VPS
   ↓
pg_dump
   ↓
compactação
   ↓
criptografia
   ↓
retenção controlada
```

Idealmente deverá existir pelo menos uma cópia fora do banco principal.

Backup deve ser testado por restauração.

Backup não é considerado válido apenas porque o arquivo foi gerado.

> **Destino do backup DEFINIDO E CONFIRMADO em 21/09/2026 (resposta à seção 122, item 18) — ver detalhamento completo na seção 131.9.** Decisão final: **Cloudflare R2** como destino externo dos backups, com rotação 7 diárias + 4 semanais + 3 mensais.

---

# 4. Estado atual que deve ser preservado

## 4.1 Stack atual

Frontend:

- Angular 18.2;
- Standalone Components;
- Lazy loading;
- TypeScript 5.5;
- Tailwind CSS;
- RxJS;
- jsPDF;
- html2canvas.

Backend atual:

- Supabase PostgreSQL;
- Supabase Auth;
- Supabase Storage;
- Supabase RLS;
- Supabase RPC;
- Edge Functions Deno;
- Cloudflare Turnstile.

O sistema ainda não possui backend Java como núcleo funcional.

---

# 5. Funcionalidades existentes que precisam continuar funcionando

- autenticação;
- cadastro de coroinhas/acólitos;
- responsáveis;
- inscrição pública;
- aprovação/rejeição de inscrições;
- upload de fotos;
- montagem de escalas;
- status de escala;
- exportação PDF/PNG;
- dashboard;
- relatórios básicos.

Nenhuma migração poderá considerar concluído um módulo se uma regra existente tiver desaparecido sem decisão explícita.

---

# 6. Problemas atuais a serem corrigidos

O sistema atual possui riscos conhecidos:

- schema real de produção parcialmente fora do controle de versão;
- RPCs existentes em produção e ausentes no repositório;
- policies RLS potencialmente permissivas;
- operações multi-etapas sem transação;
- regras duplicadas entre Angular e banco;
- frontend acessando backend Supabase diretamente;
- ausência de modelo SaaS formal;
- ausência de isolamento multi-tenant;
- ausência de backoffice;
- ausência de billing;
- ausência de testes relevantes;
- ausência de CI/CD formal;
- ausência de observabilidade estruturada.

---

# 7. Regra principal da migração

Antes de criar endpoints Java:

> reconstruir o estado real do banco atual de forma reproduzível.

Obrigatório levantar:

1. tabelas;
2. colunas;
3. ENUMs;
4. primary keys;
5. foreign keys;
6. unique constraints;
7. indexes;
8. triggers;
9. functions;
10. RPCs;
11. policies;
12. buckets;
13. regras de Storage;
14. Edge Functions;
15. regras existentes somente no Angular.

> **Nota (21/09/2026):** todo esse levantamento já foi concluído ao vivo no painel do Supabase e está documentado na seção 20 do `DOCUMENTACAO-COMPLETA-PARA-IA.md`. O critério de saída desta fase já pode ser considerado atendido.

---

# 8. Schema canônico

Deve existir uma estrutura versionada.

Exemplo:

```text
database/
  migrations/
    V001__baseline.sql
    V002__tenant.sql
    V003__usuarios.sql
    V004__voluntarios.sql
    V005__inscricoes.sql
    V006__escalas.sql
    V007__arquivos.sql
    ...
```

Toda mudança futura de estrutura deverá passar por Flyway.

Proibido alterar produção manualmente e deixar a alteração fora do repositório.

---

# 9. Baseline Flyway

Existe diferença entre:

```text
banco atual já existente
```

e:

```text
banco criado do zero
```

Para o banco existente, deverá ser definido um baseline Flyway coerente.

Fluxo conceitual:

```text
Produção atual
   ↓
dump completo
   ↓
schema validado
   ↓
baseline
   ↓
Flyway passa a controlar versões futuras
```

Para ambientes novos:

```text
PostgreSQL limpo
   ↓
Flyway
   ↓
V001
V002
V003
...
```

O schema gerado do zero deverá ser semanticamente equivalente ao esperado.

---

# 10. Arquitetura alvo do MVP

```text
                    Git
                     |
          +----------+-----------+
          |                      |
          v                      v
   Cloudflare Pages         VPS ICP CORE
          |                      |
          |                      |
      Angular SPA          Docker / HTTPS
          |                      |
          +------ HTTPS -------->|
                                 v
                         Spring Boot API
                                 |
                      +----------+----------+
                      |                     |
                      v                     v
             Supabase PostgreSQL     Supabase Storage
```

---

# 11. Estrutura do VPS

Estrutura inicial recomendada:

```text
VPS
 |
 +-- reverse-proxy
 |     Caddy ou Nginx
 |
 +-- api
 |     Spring Boot
 |
 +-- backup-job
       pg_dump / scripts
```

Docker Compose é suficiente no MVP.

Não introduzir Kubernetes.

Não introduzir orquestração complexa sem necessidade.

---

# 12. HTTPS do backend

O backend deverá ser publicado em domínio próprio.

Exemplo:

```text
api.servire.app.br
```

> **Domínio oficial definido em 21/09/2026 (resposta à seção 122, item 16): `servirea.com.br`. ✅ Registrado em 22/09/2026 (confirmado pelo usuário, print do painel do registro.br — status "Publicado", expira 22/09/2027). Verificação do domínio no painel do Resend (MX + TXT/SPF + TXT/DKIM via Cloudflare) ainda pendente — ver seção "Fase 11" do README.md.** Ver seção 131.8. Os exemplos `servire.app.br` / `app.servire.app.br` neste documento são placeholders anteriores a essa decisão e deverão ser atualizados para o domínio real (`servirea.com.br` e subdomínios, ex. `api.servirea.com.br`, `app.servirea.com.br`) quando o domínio for registrado.

Fluxo:

```text
Internet
  ↓
HTTPS
  ↓
Caddy/Nginx
  ↓
Spring Boot
```

O Spring Boot não deve ser exposto diretamente em porta administrativa para a internet.

---

# 13. Cloudflare Pages

O Angular será publicado via integração com Git.

Fluxo:

```text
git push
   ↓
Cloudflare Pages
   ↓
npm install
   ↓
ng build
   ↓
deploy
```

A aplicação é SPA.

Configurar corretamente fallback de rotas para evitar 404 em URLs internas.

---

# 14. Variáveis do frontend

O Angular poderá conter somente configurações públicas.

Exemplo:

```text
API_BASE_URL
TURNSTILE_SITE_KEY
```

Nunca colocar no frontend:

- senha do PostgreSQL;
- Supabase service role;
- S3-compatible access secret;
- JWT signing secret;
- refresh-token secret;
- credenciais administrativas;
- chaves privadas.

---

# 15. Backend alvo

Tecnologias:

- Java 21;
- Spring Boot 3.x;
- Spring MVC;
- Virtual Threads;
- Hibernate 6;
- Spring Data JPA;
- Spring Security;
- JWT;
- Flyway;
- PostgreSQL;
- Testcontainers.

Configuração:

```properties
spring.threads.virtual.enabled=true
```

A aplicação permanecerá síncrona.

Não existe justificativa atual para WebFlux.

---

# 16. Estrutura do projeto Java

Organizar por domínio.

Exemplo:

```text
src/main/java/br/com/.../

  config/
  security/
  tenant/
  auth/
  backoffice/

  voluntario/
    controller/
    service/
    repository/
    entity/
    dto/

  inscricao/
  escala/
  arquivo/
  billing/
  auditoria/
```

Evitar uma estrutura global contendo:

```text
controller/
service/
repository/
entity/
```

com centenas de classes de domínios diferentes misturadas.

---

# 17. Multi-tenancy oficial do MVP

A estratégia será:

```text
Discriminator-based Multi-Tenancy
```

Cada registro pertencente a uma paróquia possuirá:

```text
tenant_id
```

Exemplo:

```text
voluntario
----------
id
tenant_id
nome
tipo
ativo
...
```

---

# 18. Hibernate @TenantId

Hibernate 6 possui suporte a multi-tenancy por discriminador.

Entidades tenant-aware deverão possuir conceitualmente:

```java
@TenantId
private UUID tenantId;
```

O Hibernate deverá obter o tenant atual através de um:

```text
CurrentTenantIdentifierResolver
```

ligado ao contexto da requisição.

Fluxo:

```text
JWT
 ↓
Security Filter
 ↓
tenantId
 ↓
TenantContext
 ↓
CurrentTenantIdentifierResolver
 ↓
Hibernate Session
 ↓
WHERE tenant_id = ?
```

---

# 19. Regra P0 — SQL nativo

A filtragem automática do `@TenantId` não protege SQL nativo automaticamente.

Portanto:

> SQL nativo em tabelas tenant-aware é proibido por padrão.

Quando inevitável, deverá possuir:

1. justificativa;
2. filtro explícito por `tenant_id`;
3. parâmetro vindo do contexto autenticado;
4. teste de isolamento;
5. revisão de segurança.

Nunca:

```sql
SELECT * FROM voluntario;
```

em código de tenant.

Obrigatório:

```sql
SELECT *
FROM voluntario
WHERE tenant_id = :tenantId;
```

em qualquer acesso nativo autorizado.

---

# 20. TenantContext

O backend determinará o tenant da requisição autenticada.

Exemplo conceitual:

```java
try {
    TenantContext.set(tenantId);
    chain.doFilter(request, response);
} finally {
    TenantContext.clear();
}
```

Nunca deixar TenantContext persistente.

O `tenant_id` normal não será obtido do body enviado pelo Angular.

---

# 21. Origem do tenant

Para APIs autenticadas:

```text
JWT
+
vínculo usuario_tenant validado no banco
```

Para APIs públicas:

```text
slug público validado
```

Exemplo:

```text
/public/paroquia-sao-jose/inscricoes
```

Nunca confiar em:

```json
{
  "tenantId": "qualquer-id-enviado-pelo-cliente"
}
```

---

# 22. IDs globais

Utilizar preferencialmente UUID para:

- tenant;
- usuário;
- entidades principais.

Motivo:

- evita dependência de sequences globais;
- facilita referências de auditoria;
- facilita futuras migrações;
- reduz conflitos ao mover dados entre ambientes;
- facilita eventual evolução para database-per-tenant.

---

# 23. Integridade entre tenants no banco

O filtro do Hibernate não é suficiente para garantir integridade referencial.

Exemplo de risco:

```text
responsavel do Tenant A
    ↓
voluntario_id pertencente ao Tenant B
```

Mesmo que a aplicação nunca mostre o registro, o banco não deve permitir esse relacionamento.

Portanto, foreign keys tenant-aware deverão considerar o tenant.

Exemplo conceitual:

```sql
UNIQUE (tenant_id, id)
```

em `voluntario`.

E:

```sql
FOREIGN KEY (tenant_id, voluntario_id)
REFERENCES voluntario (tenant_id, id)
```

em `responsavel`.

Esse padrão deve ser aplicado onde houver relacionamento entre entidades tenant-aware.

---

# 24. Unique constraints multi-tenant

Constraints de negócio geralmente deverão incluir o tenant.

Exemplo:

Em vez de:

```sql
UNIQUE (codigo)
```

usar:

```sql
UNIQUE (tenant_id, codigo)
```

quando a unicidade deve existir apenas dentro da paróquia.

Isso vale para:

- códigos;
- nomes técnicos;
- identificadores funcionais;
- posições de escala;
- outras chaves de negócio.

---

# 25. Tabelas globais

Nem toda tabela possuirá `tenant_id`.

Tabelas globais iniciais:

```text
tenant
usuario
usuario_tenant
refresh_token
plano
preco_plano
assinatura
cobranca
webhook_event
```

Estas tabelas formam o:

```text
Master lógico
```

O Master continua existindo conceitualmente, mas não como outro banco físico.

---

# 26. Tabelas tenant-aware

Exemplos:

```text
voluntario
responsavel
inscricao
inscricao_responsavel
escala
escala_evento
escala_vaga
arquivo
audit_log
configuracao_funcao_escala
```

Todas deverão possuir:

```text
tenant_id NOT NULL
```

quando fizer sentido funcional.

---

# 27. Modelo tenant

Exemplo:

```text
tenant
------
id
codigo
slug
nome
razao_social
cnpj
status
created_at
updated_at
```

Status iniciais:

```text
ATIVO
TRIAL
BLOQUEADO
CANCELADO
```

---

# 28. Kill Switch

O tenant será autoridade central de acesso.

Fluxo:

```text
requisição
 ↓
JWT válido
 ↓
usuário ativo?
 ↓
tenant existe?
 ↓
usuario_tenant existe?
 ↓
status permite acesso?
 ↓
continua
```

Política inicial:

```text
ATIVO      → acesso normal
TRIAL      → acesso normal
BLOQUEADO  → negar operações conforme política comercial
CANCELADO  → negar acesso normal
```

~~Ainda precisa ser decidido se `BLOQUEADO` permitirá modo somente leitura.~~ **Decidido em 21/09/2026 (resposta à seção 122, item 7): não. `BLOQUEADO` nega acesso totalmente, sem modo somente leitura.** Ver seção 131.4. Isso simplifica o Kill Switch: `BLOQUEADO` e `CANCELADO` passam a ter o mesmo comportamento de negação de acesso (podem inclusive compartilhar a mesma checagem no filtro), diferindo apenas semanticamente (um é reversível mediante pagamento, o outro não).

---

# 29. Usuário global e vínculo multi-paróquia

A arquitetura será preparada desde o início para um usuário poder acessar mais de uma paróquia.

Mesmo que o MVP comercial inicialmente utilize apenas um tenant por usuário, o modelo continuará N:N.

```text
usuario
   |
   +---- usuario_tenant ---- tenant A
   |
   +---- usuario_tenant ---- tenant B
```

Tabela:

```text
usuario_tenant
--------------
usuario_id
tenant_id
role
status
```

Unique:

```text
(usuario_id, tenant_id)
```

> **CONFIRMADO em 21/09/2026:** a resposta registrada à seção 122, item 1 (ver seção 131.1) foi confirmada explicitamente pelo usuário — um mesmo usuário **realmente vai gerenciar múltiplas paróquias já no MVP**, trocando de paróquia ativa dentro do sistema (não é só a arquitetura preparada para o futuro). O usuário deu como exemplo de referência de UX o seletor de "condomínio ativo" de um sistema de gestão de condomínios que ele conhece (ver seção 131.1 para o detalhe): um seletor no topo da tela, ao lado do nome do usuário, que lista as unidades (no caso do Servire, as paróquias) disponíveis para aquele usuário e, ao trocar a seleção, recarrega o dashboard/tela atual já filtrado pela paróquia escolhida. Isso eleva definitivamente a prioridade da FASE 10 (multi-tenant real) e da tela/seletor de seleção de tenant (seção 30): deixam de ser "validação de arquitetura" e passam a ser fluxo real de uso desde o lançamento.

---

# 30. Seleção de tenant no login

Se usuário tiver somente um tenant:

```text
login
 ↓
tenant único
 ↓
JWT
```

Se possuir múltiplos:

```text
login
 ↓
lista tenants autorizados
 ↓
seleção
 ↓
JWT associado ao tenant escolhido
```

Endpoint conceitual:

```text
POST /auth/login
POST /auth/select-tenant
```

> **Referência de UX confirmada em 21/09/2026 (ver seção 131.1):** além da seleção no momento do login, o usuário quer também um **seletor de paróquia ativa sempre visível no topo da tela** (ex.: ao lado do nome/avatar do usuário logado), permitindo trocar de paróquia a qualquer momento durante o uso, não só uma vez no login — com a tela atual recarregando os dados já filtrados pela paróquia recém-selecionada. Esse padrão é análogo ao seletor de condomínio ativo que o usuário mostrou como referência de um outro sistema (gestão de condomínios), onde o nome da entidade selecionada (lá "condomínio", aqui "paróquia") aparece fixo no cabeçalho e um dropdown permite buscar e trocar para outra a qualquer momento, recarregando a tela com os dados da nova seleção.

---

# 31. Roles e permissões

Roles iniciais:

```text
ADMIN
COORDENADOR
VISUALIZADOR
```

Evitar espalhar verificações rígidas de role por todo o sistema.

> **✅ Implementado na Fase 11 (22/09/2026).** `security/Permissao.java` (as
> 7 permissões abaixo) + `security/RolePermissoes.java` (mapeamento role ->
> permissions) + `@PreAuthorize("hasAuthority('PERM_...')")` em
> `VoluntarioController`/`EscalaController`/`InscricaoController`. O `mvn
> clean verify` real de 22/09/2026 (`BUILD SUCCESS`) confirma que o
> contexto Spring sobe com `@EnableMethodSecurity` ativo, mas **não**
> confirma a aplicação real do `@PreAuthorize` — nenhum teste `MockMvc`/
> `TestRestTemplate` chama os controllers com roles diferentes para checar
> 200 vs 403. Ver README.md, seção "Fase 11", item 1, para o critério de
> mapeamento adotado (não detalhado nesta seção) e os riscos residuais.

Preferir permissões conceituais:

```text
VOLUNTARIO_READ
VOLUNTARIO_WRITE
ESCALA_READ
ESCALA_WRITE
INSCRICAO_READ
INSCRICAO_APPROVE
CONFIG_WRITE
```

Mapeamento:

```text
role
 ↓
permissions
```

Isso facilita adicionar futuramente:

- ~~PADRE~~ **(decidido em 21/09/2026, seção 122 item 2, ver 131.2: o padre NÃO terá role própria — não entra no sistema. Item removido desta lista.)**
- SECRETARIA;
- RESPONSAVEL **(decidido em 21/09/2026, seção 122 item 13, ver 131.6: ainda não no MVP — "responsável" segue sem portal próprio por enquanto, mas o role conceitual permanece reservado para quando essa decisão mudar)**;
- SUPORTE.

---

# 32. Autenticação própria

Supabase Auth será removido progressivamente.

Implementar:

```text
POST /auth/login
POST /auth/refresh
POST /auth/logout
POST /auth/forgot-password
POST /auth/reset-password
```

Senha:

```text
Argon2
```

ou:

```text
BCrypt
```

Nunca armazenar senha reversível.

---

# 33. JWT

Claims possíveis:

```json
{
  "sub": "usuario-uuid",
  "tenant": "tenant-uuid",
  "roles": ["ADMIN"]
}
```

O backend não confiará apenas nos claims.

Também deverá validar:

- usuário ativo;
- tenant existente;
- tenant com status permitido;
- vínculo `usuario_tenant` existente;
- vínculo ativo;
- permissões necessárias.

---

# 34. Access Token

Sugestão inicial:

```text
15 minutos
```

Curto o suficiente para limitar exposição.

---

# 35. Refresh Token

Sugestão inicial:

```text
7 a 30 dias
```

Tabela:

```text
refresh_token
-------------
id
usuario_id
token_hash
created_at
expires_at
revoked_at
replaced_by
ip
user_agent
```

Nunca armazenar o token puro.

---

# 36. Refresh Token Rotation

Refresh tokens deverão ser rotacionados.

Fluxo:

```text
refresh A
 ↓
uso válido
 ↓
revoga A
 ↓
gera B
 ↓
cliente recebe B
```

Reutilização de token já revogado deverá ser considerada evento de segurança.

---

# 37. Voluntários

Tipos:

```text
COROINHA
ACOLITO
AMBOS
```

Status operacional:

```text
ativo = true / false
```

Campos existentes a preservar:

- nome completo;
- nascimento;
- tipo;
- etapa da catequese;
- ano de eucaristia;
- ano de crisma;
- endereço;
- telefone;
- celular;
- e-mail;
- horário de estudo;
- observações;
- autorização WhatsApp;
- funções habilitadas;
- foto.

> **Nota (21/09/2026, seção 122 item 12, ver 131.5):** decidido que **haverá disponibilidade do voluntário** (dias/horários em que pode servir). Isso implica uma nova entidade tenant-aware futura, ex. `disponibilidade_voluntario` (voluntario_id, dia_semana ou data, período), a ser usada pelo picker de candidatos (seção 49) como filtro adicional.
>
> **✅ Entidade implementada na Fase 11 (22/09/2026).**
> `voluntario/DisponibilidadeVoluntario.java` (migration V025). O `mvn
> clean verify` real de 22/09/2026 (`BUILD SUCCESS`) confirma que o
> mapeamento JPA/migration bate com o Postgres real (`ddl-auto: validate`),
> mas **não** confirma o comportamento de
> `DisponibilidadeVoluntarioService`/`DisponibilidadeVoluntarioController`
> — nenhum teste existente chama nenhum dos dois. A integração com o
> picker de candidatos (seção 49) continua pendente, já que o próprio
> endpoint do picker ainda não existe no backend Java. Ver README.md,
> seção "Fase 11", item 3.
>
> **Nota (21/09/2026, seção 122 item 11, ver 131.5):** decidido que **haverá controle de faltas**. Isso implica um novo estado/registro por participação em escala (ex. `escala_vaga.presenca` ou tabela própria `falta`, tenant-aware), distinto da simples alocação na vaga.
>
> **✅ Implementado na Fase 11 (22/09/2026).** Optou-se pela primeira
> alternativa — coluna `presenca` em `escala_vagas` (migration V024), não
> uma tabela própria (ver comentário da migration para o porquê).
> Endpoint `PATCH /escalas/vagas/{vagaId}/presenca`. O `mvn clean verify`
> real de 22/09/2026 (`BUILD SUCCESS`) confirma o mapeamento JPA/enum
> nativo (`presenca_vaga`) contra o Postgres real, mas **não** confirma
> `EscalaService.registrarPresenca` em si — nenhum teste existente o
> chama. Ver README.md, seção "Fase 11", item 2.

---

# 38. Responsáveis

Cada voluntário pode possuir múltiplos responsáveis.

Regra:

```text
deve existir exatamente um responsável principal
```

Campos:

- parentesco;
- nome;
- telefone;
- celular;
- e-mail;
- principal.

Essa regra deverá existir no backend e ter cobertura de testes.

> **Nota (21/09/2026, seção 122 item 13, ver 131.6):** decidido que o responsável **ainda não terá portal próprio** no MVP. Ele continua sendo apenas um registro de contato vinculado ao voluntário, sem login.

---

# 39. Transação de voluntário

Operações relacionais deverão utilizar:

```java
@Transactional
```

Exemplo:

```text
voluntario
+
responsaveis
+
metadata do arquivo
```

devem ser consistentes.

O arquivo físico do Storage não participa da transação SQL.

---

# 40. Inscrições públicas

Fluxo:

```text
Visitante
 ↓
Formulário público
 ↓
Turnstile
 ↓
Tenant por slug
 ↓
Cadastro PENDENTE
 ↓
Administrador avalia
 ↓
APROVAR / REJEITAR
```

Status:

```text
PENDENTE
APROVADA
REJEITADA
```

> **Ver também:** seção 20.9 do `DOCUMENTACAO-COMPLETA-PARA-IA.md` — pedido do usuário (21/09/2026) para que o link de inscrição pública tenha expiração configurável, além do rate limit já previsto na seção 45 abaixo. Ainda não é uma decisão de design fechada; entra como requisito novo a considerar nesta fase do fluxo de inscrições.

---

# 41. Aprovação de inscrição

Na aprovação:

1. validar tenant;
2. validar status PENDENTE;
3. criar voluntário;
4. copiar responsáveis;
5. associar foto;
6. registrar usuário responsável;
7. registrar data;
8. associar inscrição ao voluntário;
9. auditar operação.

Tudo que for SQL deverá estar em uma única transação.

---

# 42. Rejeição de inscrição

Na rejeição:

- exigir motivo;
- registrar usuário;
- registrar data;
- registrar tenant;
- auditar.

---

# 43. Endpoint público

Exemplo:

```text
POST /public/{tenantSlug}/inscricoes
```

Não exigir JWT.

Obrigatório:

- Turnstile;
- rate limit;
- validação server-side;
- tenant por slug;
- limites de payload;
- validação de arquivo;
- proteção contra abuso.

---

# 44. Turnstile

Falha fechada.

Nunca:

```text
secret ausente → permitir
```

Sempre:

```text
secret ausente → falhar
```

Criar:

```text
TurnstileService
```

A validação deverá ocorrer no backend.

---

# 45. Rate Limit

Obrigatório nos endpoints públicos.

MVP pode iniciar com:

```text
limite por IP
+
Turnstile
```

Possíveis soluções:

- Bucket4j;
- reverse proxy;
- Cloudflare;
- Redis somente se realmente necessário no futuro.

Evitar introduzir Redis apenas para esse requisito se a infraestrutura atual não exigir.

---

# 46. Escalas

Operações de escala devem ser transacionais.

Fluxo atual:

```text
DELETE eventos
INSERT eventos
INSERT vagas
```

poderá continuar inicialmente se preservar o comportamento.

Prioridade:

```text
atomicidade
```

Depois:

```text
diff incremental
```

---

# 47. Controle otimista

Adicionar:

```java
@Version
private Long version;
```

nas entidades em que concorrência de edição seja relevante, especialmente escala.

Conflito:

```text
HTTP 409 CONFLICT
```

Mensagem possível:

```text
A escala foi alterada por outro usuário. Atualize a página.
```

---

# 48. Regras de vagas

Existe inconsistência conhecida:

```text
Builder mensal:
COLETA = 1

Export:
COLETA = 4
```

Não migrar essa divergência silenciosamente.

Criar fonte única de verdade.

Preferencialmente:

```text
configuracao_funcao_escala
```

tenant-aware.

---

# 49. Picker de candidatos

Endpoint conceitual:

```text
GET /escalas/{eventoId}/candidatos?funcao=MISSAL
```

Backend filtra:

- tenant atual;
- voluntário ativo;
- função habilitada;
- tipo compatível;
- não duplicado no evento;
- disponibilidade futura quando implementada.

Mesmo que Angular filtre, backend valida novamente.

> **Nota (21/09/2026):** "disponibilidade futura" deixa de ser condicional — ver decisão da seção 122 item 12 (131.5): a disponibilidade do voluntário **vai** ser implementada, então este filtro deve ser tratado como requisito confirmado do backend, não como possibilidade.

---

# 50. Validação de escala

Backend deve impedir:

- voluntário de outro tenant;
- voluntário duplicado no mesmo evento;
- vaga duplicada;
- posição inválida;
- voluntário inativo;
- função incompatível;
- conflito de versão.

Banco também deverá possuir constraints aplicáveis.

---

# 51. Supabase Storage no MVP

Storage permanecerá no Supabase.

Bucket recomendado:

```text
privado
```

Estrutura de object key:

```text
tenant/{tenantId}/voluntarios/{voluntarioId}/{uuid}.jpg

tenant/{tenantId}/inscricoes/{inscricaoId}/{uuid}.jpg
```

Nunca usar apenas:

```text
foto.jpg
```

como chave permanente.

---

# 52. Metadata de arquivo

Tabela tenant-aware:

```text
arquivo
-------
id
tenant_id
object_key
original_name
mime_type
size
sha256
status
created_at
created_by
```

Não armazenar URL assinada definitiva.

---

# 53. Integração Java com Supabase Storage

Preferência para integração server-side.

Supabase Storage oferece API compatível com protocolo S3.

Portanto a aplicação poderá encapsular Storage atrás de uma interface:

```java
interface FileStorage {
    upload(...);
    delete(...);
    createSignedUrl(...);
}
```

Implementação MVP:

```text
SupabaseStorage
```

Implementação futura possível:

```text
S3Storage
```

Assim uma futura migração para AWS S3 ou outro storage não contaminará regras de negócio.

---

# 54. Credenciais de Storage

Chaves server-side nunca vão para o Angular.

As credenciais S3-compatible do Supabase possuem alto nível de acesso.

Portanto:

- somente backend;
- variáveis de ambiente/secret do VPS;
- nunca no Git;
- nunca em log;
- nunca em resposta HTTP.

Como o backend poderá possuir acesso amplo ao bucket, o isolamento por tenant precisa ser garantido pela própria API.

---

# 55. Download de arquivos

Fluxo:

```text
Angular
 ↓
GET /arquivos/{id}
 ↓
Spring busca metadata no tenant atual
 ↓
valida autorização
 ↓
gera URL temporária
 ↓
retorna signed URL
```

O Angular nunca poderá montar object key arbitrária e solicitar arquivo de outro tenant.

---

# 56. Upload seguro

Obrigatório:

- tamanho máximo;
- MIME permitido;
- validação de conteúdo;
- nome interno aleatório;
- object key contendo tenant;
- remoção de EXIF de fotos quando aplicável;
- normalização/re-encode de imagem;
- bucket privado;
- limitação de resolução;
- proteção contra arquivos órfãos.

---

# 57. Banco + Storage não formam uma única transação

Estratégia compensatória.

Exemplo:

```text
upload arquivo TEMP
 ↓
transação SQL
 ↓
cria voluntário
cria metadata
 ↓
commit
 ↓
arquivo ACTIVE
```

Arquivos TEMP abandonados:

```text
job periódico
 ↓
limpeza
```

Nunca assumir atomicidade distribuída entre PostgreSQL e Storage.

---

# 58. LGPD

O sistema contém dados de menores.

Obrigatório considerar:

- minimização;
- consentimento;
- retenção;
- exclusão/anônimização;
- auditoria;
- controle de acesso;
- proteção de fotos;
- rastreabilidade.

Evitar logs contendo:

- telefone;
- endereço;
- e-mail;
- fotos;
- payload completo de inscrição;
- token;
- senha.

> **Política de retenção DEFINIDA E CONFIRMADA em 21/09/2026 (resposta à seção 122, item 17) — ver detalhamento completo na seção 131.7.** Decisão final: adotada integralmente a recomendação do assistente como política vigente (sujeita a validação jurídica formal antes de operar com dados reais de mais de uma paróquia, conforme ressalva mantida na seção 131.7). Resumo: inscrição REJEITADA → anonimizar/excluir dados diretos em até 30 dias; voluntário/inscrição que ficar `ativo=false` → manter por até 24 meses para fins de histórico de escalas, depois anonimizar nome/endereço/contato/foto mantendo apenas o vínculo genérico com o histórico; fotos → excluídas em prazo mais curto que os demais dados (6 a 12 meses de inatividade); adicionar consentimento explícito no formulário de inscrição (hoje só existe a autorização de WhatsApp, sem checkbox de consentimento LGPD/tratamento de dados do menor).

---

# 59. Auditoria

Tabela tenant-aware:

```text
audit_log
---------
id
tenant_id
user_id
acao
entidade
entidade_id
changed_fields
ip
request_id
created_at
```

Evitar transformar `audit_log` em cópia completa dos dados pessoais.

Preferir registrar:

```json
{
  "changedFields": [
    "telefone",
    "endereco"
  ]
}
```

quando o conteúdo anterior/novo não for necessário.

> **✅ Implementado na Fase 11 (22/09/2026).** Novo pacote `audit/`
> (`AuditLog`/`AuditLogRepository`/`AuditLogService`/`AuditLogController`,
> migration V026) — `changed_fields` usa um array nativo `text[]` em vez de
> `jsonb` (mesma informação, mapeamento JPA mais simples, ver comentário da
> migration). Chamado explicitamente pelos serviços de negócio depois de
> cada mudança relevante, nunca via AOP genérico. **Parcialmente
> confirmado** pelo `mvn clean verify` real de 22/09/2026 (`BUILD
> SUCCESS`): como `VoluntarioService`/`EscalaService`/`InscricaoService` já
> testados chamam `AuditLogService.registrar` internamente, fica
> confirmado que o método executa sem erro de SQL (incluindo a escrita na
> coluna array) e que seus fallbacks defensivos não lançam exceção fora de
> uma requisição HTTP real. **Não confirmado:** o endpoint `GET
> /audit-log` do `AuditLogController` em si — nenhum teste o chama. Ver
> README.md, seção "Fase 11", item 4, para o detalhamento completo.

---

# 60. Backoffice

Criar frontend administrativo separado quando o produto chegar à fase correspondente.

Exemplo:

```text
app.servire.app.br
admin.servire.app.br
```

Não misturar autoridade do usuário da paróquia com operador SaaS.

---

# 61. Backoffice no banco compartilhado

Como agora existe um único banco, o backoffice usará as tabelas globais:

```text
tenant
usuario
usuario_tenant
plano
assinatura
cobranca
```

O operador SaaS não deverá receber automaticamente acesso irrestrito aos dados funcionais dos tenants.

Para suporte operacional em dados de uma paróquia:

```text
selecionar explicitamente tenant
 ↓
estabelecer TenantContext
 ↓
auditar acesso
```

Evitar um modo global invisível que desabilite filtros de tenant.

---

# 62. Billing

Criar abstração:

```java
interface BillingProvider {
    createCustomer();
    createSubscription();
    createCharge();
    cancelSubscription();
}
```

Implementações possíveis:

```text
AsaasBillingProvider
StripeBillingProvider
```

Código do provedor não deve se espalhar pelas regras do sistema.

> **Gateway de pagamento (21/09/2026, resposta à seção 122 item 15, ver 131.3):** decisão provisória do usuário é operar **somente com PIX, controlado manualmente por ele**, sem gateway integrado por enquanto. Isso significa que, no MVP inicial, a interface `BillingProvider` acima pode ficar sem implementação automática — cobrança e liberação de acesso feitas manualmente pelo próprio usuário no backoffice (ex.: marcar assinatura como paga após confirmar o PIX). A abstração desta seção continua correta como preparo para quando um gateway (Asaas, por ser fortista em PIX no Brasil, é o candidato mais natural) for integrado.

---

# 63. Plano e preço

Evitar acoplar o plano diretamente a um único preço mutável.

Estrutura sugerida:

```text
plano
preco_plano
assinatura
cobranca
```

Assim:

```text
Plano X
2026 → preço A
2027 → preço B
```

não obriga alteração retroativa de contratos já existentes.

> **Planos definidos provisoriamente em 21/09/2026 (resposta à seção 122, itens 3, 4, 5 e 6 — ver 131.3):** nomes sugeridos pelo usuário **"Standard"** e **"Pro"**, cada um com opção de cobrança mensal e anual (com desconto no anual). Vai existir limite de voluntários por plano (valores exatos ainda não definidos — ficam como pendência menor dentro da 131.3). Trial de **7 dias**. Inadimplência: bloqueio após **3 dias** de atraso, e bloqueio é total (ver seção 28).

---

# 64. Webhooks de billing

Endpoint:

```text
POST /webhooks/{provider}
```

Obrigatório:

- validar autenticidade;
- registrar event id;
- idempotência;
- auditoria;
- tratamento de retry.

Tabela:

```text
webhook_event
```

Unique:

```text
external_event_id
```

Nunca processar duas vezes o mesmo evento.

> **Nota (21/09/2026):** como a decisão provisória da seção 62 é operar sem gateway integrado (PIX manual), esta seção de webhooks fica sem uso imediato — mantida como preparo para quando um gateway real for integrado.

---

# 65. Observabilidade de baixo custo

Desde o primeiro deploy Java:

Logs JSON contendo:

```text
timestamp
level
requestId
tenantId
userId
endpoint
status
duration
```

Nunca logar:

- senha;
- access token;
- refresh token;
- secret;
- payload sensível completo.

---

# 66. Actuator

Disponibilizar healthcheck controlado.

Exemplo interno:

```text
/actuator/health
```

Não expor endpoints administrativos do Actuator publicamente sem proteção.

---

# 67. Logs no VPS

MVP não precisa de ELK.

Utilizar:

```text
stdout
+
docker logs
+
log rotation
```

Configurar limite de tamanho e quantidade de arquivos de log para evitar encher o disco do VPS.

---

# 68. Virtual Threads

Virtual Threads ajudam no modelo síncrono, mas não aumentam a capacidade do PostgreSQL.

Exemplo:

```text
2000 requisições
 ↓
Virtual Threads
 ↓
pool JDBC limitado
 ↓
PostgreSQL
```

O pool continua sendo recurso crítico.

---

# 69. Pool JDBC

Como existe somente um banco físico no MVP:

```text
1 HikariPool
```

Isso simplifica drasticamente a arquitetura em comparação ao antigo database-per-tenant.

Não existe necessidade inicial de:

- pool por tenant;
- eviction de datasource;
- RoutingDataSource;
- PgBouncer por tenant.

Dimensionar o pool conforme:

- memória do VPS;
- capacidade do Supabase;
- carga observada.

---

# 70. Componentes removidos da arquitetura anterior

As seguintes peças deixam de ser necessárias no MVP:

```text
AbstractRoutingDataSource por tenant
TenantDataSourceManager
HikariPool por tenant
database_name em tenant
Flyway iterando em N bancos
CREATE DATABASE por cliente
provisioning de banco
RDS Proxy
PgBouncer por tenant
AWS S3 obrigatório
ECS
ECR
ALB
RDS
CloudFront para Angular
```

Isso reduz custo e complexidade operacional.

---

# 71. Provisionamento de novo cliente no MVP

Fluxo:

```text
Backoffice cria tenant
 ↓
INSERT tenant
 ↓
cria usuário administrador ou vínculo
 ↓
configura dados iniciais
 ↓
cliente recebe acesso
```

Não existe:

```text
CREATE DATABASE
```

para cada novo cliente.

O onboarding passa a ser muito mais barato.

---

# 72. Flyway com banco único

Deploy do MVP:

```text
build
 ↓
backup
 ↓
flyway migrate
 ↓
subir nova aplicação
```

Como inicialmente existirá apenas uma instância da API, é aceitável executar Flyway no startup ou em etapa explícita do deploy.

Preferência:

```text
etapa explícita antes da API
```

Quando houver múltiplas instâncias, migrations devem ser executadas fora das instâncias da aplicação.

---

# 73. Estratégia de migration segura

Preferir:

```text
EXPAND
 ↓
MIGRATE
 ↓
CONTRACT
```

Exemplo:

Release 1:

```sql
ALTER TABLE voluntario
ADD COLUMN novo_campo VARCHAR(100);
```

Release 2:

- aplicação passa a escrever o campo;
- dados antigos são migrados.

Release 3:

- constraint mais rígida, se necessária.

Evitar deploy que exige troca de schema e aplicação de forma atomicamente impossível.

---

# 74. CI/CD do frontend

Fluxo:

```text
Git
 ↓
Cloudflare Pages
 ↓
Build Angular
 ↓
Deploy
```

Branch strategy sugerida:

```text
main → produção
develop/hml → preview ou homologação
```

---

# 75. CI/CD do backend

Primeira versão pode ser simples.

Fluxo:

```text
Git
 ↓
tests
 ↓
mvn package
 ↓
docker build
 ↓
push/pull da imagem ou build no VPS
 ↓
backup
 ↓
migration
 ↓
docker compose up -d
 ↓
healthcheck
```

Automatizar progressivamente.

---

# 76. Rollback

Antes de deploy:

- migration compatível;
- backup válido;
- versão Docker anterior conhecida.

Rollback de código não pode assumir que migration destrutiva será revertida facilmente.

Por isso migrations devem ser preferencialmente forward-compatible.

---

# 77. Testes

Projeto atual possui ausência de testes relevantes.

Isso deve mudar durante a migração.

## Backend unitário

Testar:

- inscrições;
- aprovação;
- rejeição;
- escalas;
- vagas;
- permissões;
- kill switch;
- auth;
- refresh token;
- tenant resolver.

## Integração

Utilizar:

```text
Testcontainers PostgreSQL
```

---

# 78. Teste crítico de isolamento

Esse teste é P0.

Exemplo:

```text
Tenant A
  Voluntário João

Tenant B
  Voluntário Maria

JWT Tenant A

GET /voluntarios
```

Esperado:

```text
João
```

Proibido:

```text
Maria
```

> **Status (21/09/2026): implementado no nível de repositório/Hibernate** em `TenantIsolationIntegrationTest` (`servire-api`, pacote `br.com.servire.api.tenant`), ver seção 104 para o detalhamento técnico. O cenário aqui descrito (Tenant A não vê Maria) é coberto, junto com o cenário adicional de buscar por ID forçado de outro tenant (seção 79).

---

# 79. Matriz mínima de isolamento

Testar permanentemente:

| Cenário | Resultado esperado |
|---|---|
| Tenant A lista seus voluntários | permitido |
| Tenant A tenta buscar voluntário de B por UUID | não retorna dado |
| Tenant A altera ID na URL para entidade de B | não retorna dado |
| Tenant A envia `tenant_id=B` no JSON | ignorado/rejeitado |
| Tenant A tenta arquivo de B | bloqueado |
| Tenant A tenta escala de B | bloqueado |
| Tenant A tenta vincular responsável a voluntário B | FK/serviço bloqueia |
| usuário sem `usuario_tenant` | bloqueado |
| tenant CANCELADO | bloqueado |
| tenant BLOQUEADO | conforme política |
| SQL nativo sem tenant | teste/revisão falha |
| endpoint público com slug B | opera somente no B |

> **Nota (21/09/2026):** a linha "tenant BLOQUEADO → conforme política" agora tem política definida (seção 28): bloqueio total, sem leitura. Atualizar o teste correspondente para esperar negação completa, igual a CANCELADO.

> **Status (21/09/2026):** as duas primeiras linhas ("Tenant A lista seus voluntários" e "Tenant A tenta buscar voluntário de B por UUID") já têm teste automatizado (`TenantIsolationIntegrationTest`, ver seção 104). As demais linhas dependem de módulos ainda não implementados (auth/JWT — Fase 5; arquivo, escala, responsável — Fases 6-9; endpoint público — Fase 8) e ficam pendentes até essas fases.

---

# 80. Testes de repositório

Para toda entidade tenant-aware relevante:

1. salvar dados de A;
2. salvar dados de B;
3. abrir contexto A;
4. repository deve retornar somente A;
5. trocar para B;
6. repository deve retornar somente B.

Não testar isolamento apenas pela controller.

Testar também no nível JPA/Hibernate.

> **Status (21/09/2026): feito para `Voluntario`** (a única entidade tenant-aware que existe até agora, ver seção 104) em `TenantIsolationIntegrationTest`. Repetir o mesmo padrão para cada nova entidade tenant-aware conforme as Fases 6-9 forem criando `responsavel`, `escala`, `inscricao`, etc. **Atualização (22/09/2026):** a Fase 6 expandiu `Voluntario` e criou `Responsavel` sem repetir o padrão ainda — decisão explícita do usuário de adiar testes para priorizar velocidade (ver seção 106). Pendência reconhecida, não esquecida. **Atualização (22/09/2026, rodada Fases 7/8/9):** o mesmo padrão também não foi repetido para `Inscricao`/`InscricaoResponsavel` (Fase 8) nem `Escala`/`EscalaEvento`/`EscalaVaga` (Fase 9), ambas tenant-aware — mesma decisão de adiar testes, agora acumulada em quatro módulos de negócio sem cobertura de isolamento própria (só o smoke test `ServireApiApplicationTests` exercita o contexto inteiro subindo). **Atualização (22/09/2026, Fase 10): pendência paga.** O padrão foi finalmente repetido para as quatro entidades acumuladas — `Responsavel`, `Escala`/`EscalaEvento`/`EscalaVaga` (cascata de 3 níveis, os dois níveis mais profundos checados via JPQL direto por não terem repositório dedicado) e `Inscricao`/`InscricaoResponsavel` — em três métodos novos de `TenantIsolationIntegrationTest` (ver seção 110). Todas as sete entidades tenant-aware do projeto agora têm cobertura de isolamento própria, ainda **sem confirmação de `mvn clean verify` real**.

---

# 81. Native Query Gate

Criar regra de projeto:

> qualquer `nativeQuery = true`, `JdbcTemplate` ou SQL manual em tabela tenant-aware exige revisão.

Pode existir busca automatizada no CI procurando:

```text
nativeQuery
JdbcTemplate
createNativeQuery
```

O objetivo é impedir bypass acidental do `@TenantId`.

---

# 82. RLS do PostgreSQL

RLS não será a primeira linha de isolamento do novo backend.

A primeira linha será:

```text
Spring Security
+
TenantContext
+
Hibernate @TenantId
+
constraints/FKs tenant-aware
+
testes
```

RLS poderá ser estudada posteriormente como defesa adicional.

Não manter policies antigas apenas por inércia.

Cada policy deverá ser:

- entendida;
- documentada;
- mantida conscientemente;
- ou removida após a migração do acesso direto do frontend.

---

# 83. Supabase não será removido nesta fase

A versão anterior previa desligamento completo do Supabase.

Essa decisão muda.

O objetivo agora é remover o Supabase como:

```text
backend de regras de negócio acessado diretamente pelo Angular
```

Mas manter Supabase como infraestrutura:

```text
PostgreSQL
+
Storage
```

Portanto:

```text
Supabase JS no Angular → remover progressivamente
Supabase Auth → remover
RPCs de negócio → migrar para Java
Edge Functions → migrar para Java
Supabase PostgreSQL → manter
Supabase Storage → manter
```

---

# 84. Benefício desta separação

Arquitetura nova:

```text
Angular
  ↓
Spring Boot
  ↓
PostgreSQL / Storage
```

O produto passa a depender de interfaces próprias.

Isso permite futuramente trocar:

```text
Supabase PostgreSQL
```

por:

```text
RDS PostgreSQL
Neon
PostgreSQL próprio
outro PostgreSQL gerenciado
```

sem reescrever o Angular.

Da mesma forma:

```text
Supabase Storage
```

poderá ser trocado por:

```text
AWS S3
Cloudflare R2
outro S3-compatible
```

atrás da interface de Storage.

---

# 85. Gatilhos para sair do Supabase Free

Upgrade ou migração deverá ser avaliada quando qualquer condição abaixo surgir:

- banco aproximando-se de 70–80% da cota;
- Storage aproximando-se de 70–80%;
- pausa por inatividade incompatível com SLA;
- necessidade de backup gerenciado;
- necessidade de PITR;
- performance insuficiente;
- quantidade de clientes crescendo de forma relevante;
- exigência contratual de disponibilidade;
- necessidade de recursos não disponíveis no Free.

Não esperar indisponibilidade para decidir.

---

# 86. Gatilhos para sair do VPS único

Avaliar infraestrutura mais robusta quando:

- CPU frequentemente alta;
- memória insuficiente;
- necessidade de alta disponibilidade;
- deploy causa indisponibilidade inaceitável;
- número de requests cresce significativamente;
- billing passa a justificar redundância;
- clientes passam a exigir SLA;
- necessidade de múltiplas réplicas.

Possível evolução:

```text
VPS único
 ↓
2 instâncias / load balancer
 ↓
ECS/Fargate ou equivalente
```

Somente quando necessário.

---

# 87. Gatilhos para revisar shared-database multi-tenancy

`tenant_id` em banco compartilhado é a decisão oficial do MVP.

Reavaliar somente se houver:

- clientes muito grandes;
- necessidade regulatória de isolamento físico;
- backup/restauração por cliente;
- requisito comercial enterprise;
- risco de noisy neighbor relevante;
- dezenas/centenas de milhões de registros;
- necessidade de mover um cliente para infraestrutura dedicada.

A aplicação deverá ser desenhada para que essa evolução seja possível, mas não implementada prematuramente.

---

# 88. Estratégia futura de tenant dedicado

Se algum cliente no futuro exigir isolamento físico, uma possível evolução será:

```text
Shared Database
       |
       +-- maioria dos tenants
       |
       +-- tenant enterprise
              ↓
        database dedicado
```

Não implementar isso no MVP.

A arquitetura de serviços e IDs globais deve apenas evitar bloquear essa possibilidade.

---

# 89. Segurança do VPS

Obrigatório:

- SSH por chave;
- desabilitar login root remoto quando possível;
- firewall;
- expor apenas portas necessárias;
- atualização periódica;
- Docker atualizado;
- volumes controlados;
- secrets fora da imagem;
- backups;
- log rotation;
- healthcheck;
- restart policy.

Portas públicas esperadas:

```text
22  → SSH restrito
80  → redirect
443 → HTTPS
```

Posta interna da aplicação não deve ficar aberta publicamente.

---

# 90. Secrets no VPS

Nunca versionar `.env` com segredo.

Secrets iniciais podem ser fornecidos ao container via:

```text
variáveis de ambiente
+
arquivo protegido no VPS
```

Permissões do arquivo restritas ao usuário operacional.

Futuramente migrar para um secret manager se a infraestrutura justificar.

---

# 91. CORS

Permitir somente origins conhecidas.

Exemplo:

```text
https://app.servire.app.br
```

Não utilizar indiscriminadamente:

```text
Access-Control-Allow-Origin: *
```

em endpoints autenticados.

> Ver nota da seção 12 sobre atualização do domínio para `servirea.com.br`.

---

# 92. Cookies versus Storage de token

Decisão final de transporte do refresh token deve ser tomada na implementação de auth.

Preferência de segurança:

```text
refresh token
→ cookie HttpOnly
→ Secure
→ SameSite apropriado
```

Access token pode continuar de curta duração.

Evitar expor refresh token ao JavaScript quando não houver necessidade.

> **Decisão tomada em 21/09/2026, ao iniciar a Fase 5 (ver seção 105):** cookie `HttpOnly`+`Secure`+`SameSite=None`, escopado ao path `/auth`. O usuário escolheu essa opção explicitamente (via pergunta direta) em vez de devolver o refresh token no corpo JSON.

---

# 93. CSRF

Se autenticação utilizar cookie para operações autenticadas, avaliar e implementar proteção CSRF corretamente.

Não assumir que JWT elimina CSRF automaticamente quando token estiver em cookie.

> **Implementado na Fase 5 (seção 105):** `csrf.spa()` do Spring Security 7.0 — cookie `XSRF-TOKEN` legível por JavaScript (convenção que o Angular já lê nativamente), isento em `/auth/login`, `/auth/select-tenant`, `/auth/forgot-password`, `/auth/reset-password` (não dependem de cookie), e ativo em `/auth/refresh`/`/auth/logout` (dependem do cookie do refresh token).

---

# 94. Headers de segurança

Configurar no reverse proxy e/ou Spring:

- HSTS quando HTTPS estabilizado;
- X-Content-Type-Options;
- Content-Security-Policy compatível;
- Referrer-Policy;
- proteção contra framing conforme necessidade.

---

# 95. Paginação

Listagens não devem crescer indefinidamente.

Endpoints deverão suportar:

```text
page
size
sort
filter
```

Estabelecer limite máximo de `size`.

---

# 96. Índices multi-tenant

Como `tenant_id` participa de praticamente todas as consultas, índices deverão considerá-lo.

Exemplos:

```sql
CREATE INDEX idx_voluntario_tenant_ativo
ON voluntario (tenant_id, ativo);
```

```sql
CREATE INDEX idx_escala_tenant_data
ON escala (tenant_id, data);
```

A ordem de colunas deve refletir queries reais.

---

# 97. Performance

Não otimizar prematuramente.

Primeiro:

- queries corretas;
- isolamento correto;
- índices básicos;
- N+1 controlado;
- paginação.

Depois medir.

---

# 98. Cache

Não introduzir Redis no MVP por padrão.

Cache poderá ser adicionado quando métricas mostrarem necessidade real.

Sempre considerar impacto de cache no isolamento de tenant.

Chaves de cache futuras deverão incluir:

```text
tenantId
```

---

# 99. Backoffice e acesso a tenants

Operador do SaaS nunca deverá alterar silenciosamente o tenant de uma sessão comum.

Acesso de suporte deverá ser:

- explícito;
- auditado;
- limitado;
- identificável na interface.

---

# 100. Plano de migração revisado

## FASE 0 — Inventário técnico

Objetivo:

```text
entender produção real
```

Tarefas:

- dump;
- schema;
- RPCs;
- policies;
- triggers;
- indexes;
- Storage;
- Edge Functions;
- regras Angular.

Critério de saída:

> ser capaz de explicar e reproduzir o banco atual.

> **Status (21/09/2026): critério de saída atendido.** Ver seção 7 e seção 20 do `DOCUMENTACAO-COMPLETA-PARA-IA.md`.

---

# 101. FASE 1 — Schema canônico + Flyway

Criar migrations.

Adicionar baseline.

Subir PostgreSQL limpo em testes.

Executar migrations.

Comparar estrutura.

Ainda não migrar Angular.

> **Status (21/09/2026): FASE 1 concluída.** As 15 migrations do baseline (`V001` a `V015`) foram criadas cobrindo os 5 ENUMs, as 7 tabelas de domínio, a view, as 3 RPCs de negócio (copiadas literalmente do painel), os wrappers públicos, os grants e todas as RLS policies. Validadas rodando de fato num PostgreSQL 16 limpo (local, já que Docker não estava disponível no ambiente de execução — Testcontainers de verdade fica para a FASE 2, quando o projeto Spring Boot existir): todas as asserções estruturais (`compare-schema.sql`) e um teste funcional ponta a ponta das RPCs (`functional-smoke-test.sql` — criar → aprovar inscrição, com os três papéis `anon`/`authenticated`/`service_role` simulados via `SET ROLE`) passaram. Arquivos entregues ao usuário como `servire-database-baseline.zip`; detalhamento completo em `servire-database/README.md` dentro do zip. Dois pontos ficaram marcados como "reconstruído por inferência, não extraído literalmente" e merecem uma checagem rápida ao vivo no painel antes de tratar como 100% fiel: o corpo da(s) função(ões) `set_updated_at()` e dos 3 wrappers públicos, e os nomes/predicados exatos das policies de `storage.objects` (a seção 20 do documento técnico não fez um dump de `pg_policies` para storage como fez para as tabelas de domínio).

---

# 102. FASE 2 — Fundação Spring Boot

Criar:

```text
/health
exception handling
logging
requestId
config
JPA
Flyway
Testcontainers
```

Usar banco de desenvolvimento.

> **Status (21/09/2026): FASE 2 concluída.** Projeto `servire-api` criado em Spring Boot 4.1.1 (ver decisão de versão na seção 119 — troca em relação ao "Spring Boot 3" documentado originalmente, por fim do suporte open-source da linha 3.5 em 30/06/2026; confirmada com o usuário). Entregues: `spring-boot-starter-actuator` com `/actuator/health`; hierarquia de exceções (`ApiException` e subclasses `ResourceNotFoundException`/`ConflictException`/`BadRequestException`/`ForbiddenException`) tratada por um `GlobalExceptionHandler` (`@RestControllerAdvice extends ResponseEntityExceptionHandler`) que nunca vaza detalhe interno numa resposta 500; logging estruturado em JSON via `logstash-logback-encoder`, com texto legível em dev (`logback-spring.xml` por profile); `RequestIdFilter` gerando/propagando `X-Request-Id` via MDC; profiles `dev`/`test`/`prod` (`application.yml`); Spring Data JPA com `ddl-auto: validate` (Flyway é dono do schema, não o Hibernate); as 15 migrations do baseline da FASE 0/1 (`V001`-`V015`) copiadas para `src/main/resources/db/migration`; `AbstractIntegrationTest` com Testcontainers (Postgres 16 real + stub de `auth`/`storage`/roles do Supabase, aplicado via `Statement.execute()` de arquivo único para não quebrar os blocos `DO $$...$$` — o `withInitScript()` padrão do Testcontainers faz split ingênuo por `;` e corromperia esse stub); `Dockerfile` multi-stage Maven+JRE Alpine. Entregue ao usuário como `servire-api-fase2.zip`. **Ressalva importante:** o ambiente de execução bloqueia acesso ao Maven Central (`repo1.maven.org`/`repo.maven.apache.org` retornam 403 via proxy da organização), então **não foi possível rodar `mvn clean verify` para confirmar que o projeto builda limpo** — isso está documentado explicitamente no `README.md` do zip, pedindo ao usuário para rodar o build no próprio ambiente e reportar qualquer erro de compilação. Da mesma forma, o `Dockerfile` não foi testado (sem daemon Docker disponível nesta sessão).
>
> **Atualização (21/09/2026): usuário rodou `mvn clean verify` de verdade e encontrou um erro real.** `'dependencies.dependency.version' for org.testcontainers:junit-jupiter:jar is missing` e o mesmo para `org.testcontainers:postgresql`. Causa: o Testcontainers 2.x (gerenciado pelo BOM que o Spring Boot 4.1.x importa) renomeou os artefatos Maven com prefixo `testcontainers-` (`postgresql` → `testcontainers-postgresql`, `junit-jupiter` → `testcontainers-junit-jupiter`); o BOM só gerencia versão para os nomes novos. Corrigido no `pom.xml` (zip reenviado); os pacotes Java não mudaram, nenhum código-fonte precisou de ajuste. Este é exatamente o tipo de coisa que a ressalva de "build não verificado neste ambiente" existia para capturar — funcionou como esperado.
>
> **Atualização (21/09/2026): segundo erro real encontrado e corrigido — Flyway silenciosamente não rodava.** Com Docker instalado e `mvn clean verify` rodando de fato contra um Postgres real via Testcontainers, os testes falhavam com `flyway_schema_history does not exist` e contagens de tabela zeradas, sem nenhum log do Flyway no console apesar do root logger em INFO. Causa: no Spring Boot 4, a auto-configuração do Flyway foi extraída da `spring-boot-autoconfigure` para um módulo próprio, que só é ativado com a dependência `spring-boot-starter-flyway` (o `flyway-core` bare, usado até o Boot 3.x, não é mais suficiente). Corrigido no `pom.xml`. **Confirmado com uma execução real e completa: `mvn clean verify` terminou em `BUILD SUCCESS`, todas as 15 migrations aplicadas pelo Flyway, 11 testes executados, 0 falhas, 0 erros.** A FASE 2 está, portanto, encerrada com build verificado de verdade no ambiente do usuário (Windows, Docker Desktop), não apenas no ambiente de execução desta sessão. Repositório GitHub criado e sincronizado pelo usuário em `https://github.com/GustavoToebe/servire-api` (commit inicial: "Fase 2: fundacao Spring Boot 4.1.1..."). **Observação registrada nesta fase para investigação futura, e já revisitada na Fase 3/4 (ver seção 104, bug real #3):** cada classe de teste Testcontainers estava subindo seu próprio container Postgres em vez de compartilhar o "singleton container pattern" pretendido em `AbstractIntegrationTest` (portas diferentes observadas entre classes de teste na mesma execução). Na época pareceu só deixar a suíte mais lenta; na Fase 3/4, com mais classes de teste usando o container, isso se revelou um bug real que quebrava testes (não só uma ineficiência) — corrigido na seção 104.
>
> Próximo passo recomendado: seção 103/104 (FASE 3/4 — modelo SaaS lógico: `tenant`/`usuario`/`usuario_tenant`/`refresh_token` + `TenantContext`). **Concluído — ver seção 104.**

---

# 103. FASE 3 — Modelo SaaS lógico

Criar desde cedo:

```text
tenant
usuario
usuario_tenant
refresh_token
```

Mesmo inicialmente existindo apenas:

```text
1 tenant
```

Isso evita refazer autenticação depois.

> **Status (21/09/2026): parcialmente concluída, por decisão consciente de escopo.** As 4 tabelas foram criadas via migrations Flyway (`V016__table_tenant.sql`, `V017__table_usuario.sql`, `V018__table_usuario_tenant.sql`, `V019__table_refresh_token.sql`), mais `V020__seed_tenant_inicial.sql` (semeia um tenant inicial com UUID fixo e conhecido, `00000000-0000-0000-0000-000000000001`, com `codigo`/`slug`/`nome` explicitamente marcados como PLACEHOLDER até o nome real da paróquia ser informado) e `V021__add_tenant_id_domain_tables.sql` (adiciona `tenant_id NOT NULL` às 7 tabelas de domínio existentes — `voluntarios`, `responsaveis`, `escalas`, `escala_eventos`, `escala_vagas`, `inscricoes`, `inscricao_responsaveis` —, faz o backfill de todo dado já existente para esse tenant inicial, seção 114, e troca as FKs simples entre elas por FKs compostas tenant-aware, seção 23).
>
> **Camada JPA criada só para `tenant` e `usuario`** (entidades `Tenant`/`TenantRepository` e `Usuario`/`UsuarioRepository`, pacotes `br.com.servire.api.tenant` e `br.com.servire.api.auth`). **`usuario_tenant` e `refresh_token` ficaram só no SQL, sem entidade JPA ainda** — decisão deliberada: ambas têm chave primária composta/`@MapsId`, que exige que as entidades pai (`Usuario`/`Tenant`) já estejam persistidas (com ID gerado) antes de montar o ID embutido do filho, uma ordem de operações fácil de errar sem os testes de login/JWT reais que só existirão na Fase 5 (seção 105) para exercitá-la de verdade. Melhor adiar essas duas entidades para a Fase 5, quando o fluxo de autenticação que as usa for implementado e puder testá-las de ponta a ponta, do que publicar agora um código não testado. As tabelas SQL já existem e não precisam ser recriadas quando isso acontecer. **Concluído na Fase 5 — ver seção 105** (com a correção de que `refresh_token` na verdade sempre teve chave simples, não composta; só `usuario_tenant` precisava mesmo de `@MapsId`).
>
> **Risco residual não verificado ao vivo:** o mapeamento do enum nativo do Postgres `tenant_status` em `Tenant.status` usa `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` (padrão confirmado como existente desde o Hibernate 6.5, via `PostgreSQLEnumJdbcType`), mas não foi possível reconfirmar contra a versão exata deste projeto (Hibernate ORM 7.4.5.Final) por ter esbarrado num limite de sessão de pesquisa no momento da implementação. Comentário de aviso deixado no código-fonte (`Tenant.java`); se o próximo `mvn clean verify` acusar erro de schema/tipo na coluna `status`, este é o primeiro lugar a checar. **Nota (21/09/2026): já foram 3 execuções reais de `mvn clean verify` desde a criação de `Tenant`/`V016` e nenhuma acusou erro nessa coluna — indício de que o mapeamento está correto, mas ainda não é uma reconfirmação explícita.**

---

# 104. FASE 4 — TenantContext e @TenantId

Antes de migrar módulos de negócio:

- TenantContext;
- CurrentTenantIdentifierResolver;
- `@TenantId`;
- constraints tenant-aware;
- testes de isolamento.

Inicialmente tenant pode ser fixo em desenvolvimento.

Depois passa a vir do JWT.

> **Status (21/09/2026): concluída.** Implementado em `br.com.servire.api.tenant`:
>
> - `TenantContext` — `ThreadLocal<UUID>` com `set`/`get`/`clear`; regra de nunca deixar o contexto vazar entre requisições (seção 20) documentada e aplicada via `try/finally` em todo chamador.
> - `ServireCurrentTenantIdentifierResolver` — implementa `org.hibernate.context.spi.CurrentTenantIdentifierResolver<UUID>`. Ver bug real #1 abaixo para a evolução do seu comportamento quando não há `TenantContext` definido.
> - `TenantConfiguration` — liga o resolver ao Hibernate via um bean `HibernatePropertiesCustomizer` (import confirmado para o pacote novo do Spring Boot 4.1.1, `org.springframework.boot.hibernate.autoconfigure`, já que a auto-configuração do Hibernate também foi modularizada nessa versão, no mesmo espírito da mudança do Flyway na seção 102) e `org.hibernate.cfg.MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER`. Confirmado, lendo o código-fonte de `MultiTenancySettings` (Hibernate 7.4.5.Final), que nenhum outro "liga multi-tenancy" é necessário para a estratégia por discriminador/`@TenantId` — os demais settings da classe são só para schema-per-tenant/database-per-tenant, que este projeto não usa.
> - `DevFixedTenantFilter` — andaime temporário (comentário explícito no código para remover na Fase 5): populava `TenantContext` com o tenant inicial fixo (UUID da seed `V020`) em toda requisição, já que ainda não existia JWT. Rodava logo depois do `RequestIdFilter` da Fase 2. **Removido na Fase 5 — ver seção 105.**
> - Primeira entidade tenant-aware: `br.com.servire.api.voluntario.Voluntario`, mapeando só `id`/`tenantId` (`@TenantId`)/`nomeCompleto`/`ativo` da tabela `voluntarios` — deliberadamente mínima (sem `tipo`, endereço, `funcoes_habilitadas` etc.), só o suficiente para provar o mecanismo de isolamento; entidade completa **implementada na Fase 6 — ver seção 106**.
> - Teste crítico de isolamento: `br.com.servire.api.tenant.TenantIsolationIntegrationTest`, cobrindo o cenário P0 da seção 78 (tenant A não vê voluntário de B, nem por `findAll()` nem buscando pelo ID de B diretamente) e um teste adicional de que salvar sem `TenantContext` definido nunca grava um registro com tenant errado.
>
> Todos os arquivos (6 migrations SQL + 7 classes Java) foram sincronizados para o ambiente real do usuário (Windows) via a mesma conexão direta usada na Fase 2.
>
> ### 🐛 Bug real #1 (21/09/2026): resolver derrubava a inicialização inteira do Spring
>
> Usuário rodou `mvn clean verify` de verdade e **todos** os testes falharam — inclusive `ServireApiApplicationTests`, que não tem nada a ver com tenant. Isso já era o sinal de que a causa era uma só, acontecendo na inicialização do Spring, não em cada teste individualmente.
>
> O stack trace completo (lido direto de `target/surefire-reports/` no ambiente do usuário, já que o Maven só imprime a causa raiz na primeira falha) mostrou: `BeanCreationException` ao criar o bean `tenantRepository` → `QueryCreationException` → `IllegalStateException: TenantContext não definido para a thread atual`, lançada de dentro de `ServireCurrentTenantIdentifierResolver.resolveCurrentTenantIdentifier`, chamada por `SessionFactoryImpl.resolveTenantIdentifier`.
>
> **Causa:** o Spring Data JPA sonda cada repositório por named queries já na criação do bean (`NamedQuery.hasNamedQuery`, durante `preInstantiateSingletons` — ou seja, no bootstrap do contexto, antes de qualquer requisição HTTP existir); essa sondagem cria um `EntityManager`, e sob um `SessionFactory` multi-tenant isso sempre resolve o tenant atual, mesmo para entidades globais sem `@TenantId` (`Tenant`, `Usuario`) e sem nenhuma query de verdade rodando. A primeira versão do resolver lançava `IllegalStateException` quando não havia `TenantContext`, pensando em falhar alto e cedo (seção 78/79/80) — só que essa checagem também disparava nesse momento de bootstrap, derrubando a aplicação inteira antes de qualquer teste rodar.
>
> **Correção:** o resolver não lança mais exceção — retorna um UUID sentinela reservado (`SEM_TENANT`, o UUID nulo) quando não há `TenantContext`, que nunca existe na tabela `tenant`. Isso resolve o bootstrap sem quebrar nada, e mantém a proteção P0 por outro caminho: qualquer INSERT/UPDATE real numa tabela tenant-aware sem `TenantContext` definido continua falhando, agora por violação de foreign key em vez de exceção no resolver; uma leitura sem contexto simplesmente retorna vazio. O teste de isolamento foi atualizado para esperar `DataIntegrityViolationException` (violação de FK) nesse cenário, em vez de `IllegalStateException`.
>
> **✅ Confirmado no build seguinte do usuário:** o erro genérico de "threshold exceeded" em todas as classes desapareceu — o contexto Spring volta a subir normalmente.
>
> ### 🐛 Bug real #2 (21/09/2026): contagem de migrations desatualizada no teste
>
> `FlywayMigrationIntegrationTest` ainda afirmava `total == 15`/`sucesso == 15` (bagagem da Fase 2, antes de `V016`-`V021` existirem). Com as 6 migrations novas da Fase 3/4, o total real passou a ser 21. Falha determinística e esperada, sem nada de arquitetural.
>
> **Correção:** teste atualizado para esperar `21`/`21`, e a asserção do `getLast()` trocada de `"storage bucket"` (V015) para `"add tenant id domain tables"` (V021). **Confirmado pelo build seguinte do usuário:** `FlywayMigrationIntegrationTest` passa limpo, 5/5.
>
> ### 🐛 Bug real #3 (21/09/2026): "singleton container" do Testcontainers que na prática não era singleton
>
> Depois dos bugs #1 e #2 corrigidos, sobrou uma falha real e reproduzível (em dois builds seguidos) só em `TenantIsolationIntegrationTest`: as duas tarefas falhavam com `CannotCreateTransactionException` encadeando até `Connection refused`/timeout de 30s do HikariCP — parecendo, à primeira vista, instabilidade do Docker Desktop/WSL2 (era a mesma observação já registrada, e subestimada, na Fase 2 — ver seção 102).
>
> O log completo (não só o resumo do console, que o Maven trunca) mostrou o mecanismo real: **três containers Postgres diferentes** foram criados, um por classe de teste (`FlywayMigrationIntegrationTest` na porta 51516, `ServireApiApplicationTests` na 51532, `TenantIsolationIntegrationTest` na 51535) — mesmo o campo `POSTGRES` em `AbstractIntegrationTest` sendo `static final` e todas as classes rodando na mesma fork/JVM do Surefire (sem `forkCount`/`reuseForks` customizado no `pom.xml`, confirmado). Pior: o `HikariPool` de `TenantIsolationIntegrationTest` tentava (e falhava) conectar na porta **51516** — a primeira classe, já com o container parado — em vez da sua própria, recém-criada, na 51535.
>
> **Causa raiz (duas partes):**
>
> 1. `AbstractIntegrationTest` usava `@Testcontainers` + `@Container` no campo estático — o padrão mais comum em tutoriais, mas que **não** é a forma correta de compartilhar container entre classes. Essa extensão JUnit5 gerencia o ciclo de vida por CLASSE de teste: chama `start()` no `beforeAll` e **`stop()` no `afterAll`** — mesmo em campo `static` herdado. Cada classe nova, então, parava o container da anterior e subia um novo, com porta mapeada diferente.
> 2. Isso por si só custaria só tempo (subir Postgres 3x), não corretude — o que tornou bug real foi combinar com um comportamento documentado do Spring: o cache de `ApplicationContext` do `@SpringBootTest` **não** considera os valores de `@DynamicPropertySource` na chave do cache, só as anotações estáticas da classe. Como `TenantIsolationIntegrationTest` tem a mesma configuração "estática" de `FlywayMigrationIntegrationTest` (nenhuma anotação extra além do que já está em `AbstractIntegrationTest`), o Spring reaproveitou do cache o contexto/`DataSource` já construído pela primeira classe — apontando pra um container já morto — em vez de construir um novo apontando pro container recém-subido daquela classe.
>
> **Correção:** seguir à risca o padrão oficial de "singleton container" do Testcontainers, que deliberadamente NÃO usa `@Container`/`@Testcontainers` — o container é iniciado uma única vez, manualmente, num bloco estático (`static { POSTGRES.start(); }`), e nunca é parado explicitamente (a limpeza ao final do processo continua por conta do Ryuk, como já era). Sem `@Container`, a extensão JUnit5 nunca tenta parar esse container entre classes — ele nasce uma vez e vive até o fim do `mvn`, tornando irrelevante o comportamento de cache do Spring aqui (com ou sem cache, o container é sempre o mesmo, na mesma porta).
>
> **✅ Confirmado pelo `mvn clean verify` seguinte do usuário: `BUILD SUCCESS`, 13 testes, 0 falhas, 0 erros.** Conferido diretamente em `target/surefire-reports/` no ambiente do usuário: `TenantIsolationIntegrationTest` passou em 0,427s (antes travava por volta de 60s tentando conectar num container já morto). Com os três bugs reais desta fase corrigidos e confirmados, a Fase 3/4 está encerrada sem nenhuma ressalva pendente. Próximo passo recomendado: Fase 5 (seção 105 — autenticação própria, que também é quando `usuario_tenant`/`refresh_token` ganham entidade JPA, ver seção 103).

---

# 105. FASE 5 — Autenticação própria

Implementar:

- login;
- JWT;
- refresh;
- rotation;
- logout;
- forgot password;
- reset password;
- roles/permissões.

Migrar Angular:

```text
Supabase Auth
 ↓
Spring Auth
```

> **Status (21/09/2026, código completo implementado; ✅ CONFIRMADO com `mvn clean verify` real em 21/09/2026 23:37, e o débito de testes da fase confirmado depois na Fase 10 — ver seção 110):** ver as atualizações ao longo desta seção para o histórico completo, incluindo os Bugs reais #4, #5 e #9.
>
> **Entidades JPA que faltavam desde a Fase 3 (seção 103), agora criadas:** `UsuarioTenant` (`@EmbeddedId` + `@MapsId` duplo — precisa mesmo de chave composta, `usuario_id`+`tenant_id`) e `RefreshToken` (correção de uma suposição errada da Fase 3: a migration `V019` sempre teve `id uuid PRIMARY KEY DEFAULT gen_random_uuid()` simples, não uma chave composta — adiar essa entidade não era estritamente necessário, mas também não causou dano, já que o fluxo que a usa só existe agora). Nova migration `V022__table_password_reset_token.sql` para o fluxo de "esqueci minha senha" (total de migrations passa de 21 para 22 — `FlywayMigrationIntegrationTest` atualizado de acordo).
>
> **Endpoints (`br.com.servire.api.auth.AuthController`, todos sob `/auth`):** `POST /auth/login` (login único-tenant já devolve tokens completos; multi-tenant devolve token de seleção de 5 min + lista de paróquias, seção 30), `POST /auth/select-tenant`, `POST /auth/refresh` (rotaciona o refresh token, seção 36 — reuso de token já revogado revoga TODAS as sessões do usuário, tratado como possível roubo), `POST /auth/logout`, `POST /auth/forgot-password` (sempre 202, nunca revela se o e-mail existe — anti-enumeração), `POST /auth/reset-password` (token de uso único; trocar senha revoga todos os refresh tokens do usuário, mesmo tratamento do reuso detectado).
>
> **Transporte do refresh token: cookie `HttpOnly`+`Secure`+`SameSite=None`, escopado a `/auth`** — decisão tomada com o usuário nesta fase (pergunta direta, ver seção 92 atualizada), nunca aparece em corpo de resposta JSON.
>
> **`SecurityConfig` + `JwtAuthenticationFilter` substituem por completo o `DevFixedTenantFilter` da Fase 4** (removido, como já estava previsto na sua própria javadoc e na seção 104): o filtro extrai o access token do header `Authorization`, valida via `JwtService`, e **revalida o Kill Switch (seção 28) direto no banco a cada requisição** (usuário ativo, vínculo `usuario_tenant` ATIVO, tenant ATIVO/TRIAL) — deliberado, para que bloquear um usuário/tenant tenha efeito imediato em vez de esperar o access token expirar (15 min). CSRF via `csrf.spa()` (método de conveniência do Spring Security 7.0, confirmado contra a documentação oficial — `CsrfConfigurer.spa()`, disponível desde a 7.0), isento nos 4 endpoints de auth que não dependem de cookie, ativo em `/auth/refresh`/`/auth/logout` (seção 93 atualizada). CORS restrito a `CORS_ALLOWED_ORIGINS`, nunca `*` (seção 91), com `allowCredentials(true)`.
>
> **Outras decisões desta fase:** senha via `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (BCrypt, padrão oficial do Spring Security — a seção 32 deixava "Argon2 ou BCrypt" em aberto, BCrypt escolhido para não precisar de dependência extra); refresh token e token de reset de senha usam hash **SHA-256**, não bcrypt/argon2 (são segredos de alta entropia gerados pelo servidor, não senhas humanas, e precisam de busca indexada exata `WHERE token_hash = ?`, que um hash salgado não permite — documentado em `OpaqueTokenGenerator`); `JJWT` 0.13.0 (pesquisado e confirmado como a versão mais recente em 21/09/2026) para emissão/validação, com dois `purpose` de token (`access` vs `tenant_selection`) para nunca aceitar um pelo outro; envio de e-mail do "esqueci minha senha" está stubado em `LoggingEmailSender` (só loga em DEBUG) — **não é produção-ready**, é um placeholder explícito até decidir um provedor de e-mail real com o usuário (pendência aberta, ver seção "Próximos passos" do `README.md` do repositório).
>
> **🐛 Bug real #4 (21/09/2026): `jackson-databind` sumia da compilação por mediação de dependências do Maven.** O primeiro `mvn clean verify` real desta fase, rodado pelo usuário, falhou na compilação: `package com.fasterxml.jackson.databind does not exist` em `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` (as duas classes que montam a resposta 401/403 manualmente, fora do alcance do `GlobalExceptionHandler`, usando `ObjectMapper` diretamente). **Causa raiz:** `jackson-databind` (Jackson **2**) nunca fora dependência DIRETA do projeto — só chegava transitivamente via `jjwt-jackson` (biblioteca de JWT, seção 33), que a usa como motor interno de (de)serialização de claims. *Correção ao texto original desta seção, feita ao investigar o bug real #5 logo abaixo:* a primeira versão deste parágrafo dizia que o caminho transitivo era `spring-boot-starter-web` → `spring-boot-starter-json` — isso está ERRADO; a partir do Spring Boot 4, `spring-boot-starter-web` não traz Jackson 2 nenhum, e sim Jackson **3** (grupo `tools.jackson.*`), um jar sem nenhuma relação com `com.fasterxml.jackson.databind`. Ao adicionar `spring-security-test` (escopo `test`, que também traz Jackson 2 transitivamente) nesta mesma fase, o Maven passou a resolver `jackson-databind` por um caminho mais RASO da árvore (via `spring-security-test`) do que o caminho antigo (via `jjwt-jackson`) — a regra de mediação "nearest definition" do Maven usa a declaração mais próxima, mesmo que o escopo dela (`test`) seja mais restrito que o de uma declaração mais distante (`compile`), escondendo silenciosamente a classe do classpath de compilação principal. **Correção:** declarar `com.fasterxml.jackson.core:jackson-databind` como dependência DIRETA (profundidade 1, sem escopo = `compile`) no `pom.xml`, o que garante vitória na mediação por profundidade contra qualquer caminho transitivo mais fundo. **✅ Confirmado pelo `mvn clean verify` seguinte do usuário**: o erro de compilação desapareceu (48 arquivos-fonte compilaram). Esse mesmo build revelou o bug real #5.
>
> **🐛 Bug real #5 (21/09/2026): `ObjectMapper` do Jackson 2 nunca existiu como bean no Spring Boot 4 — o app usa Jackson 3.** Segundo `mvn clean verify` real (já com o bug #4 corrigido): compilação passou, mas a `ApplicationContext` do Spring falhou ao subir em todo teste que carrega o contexto inteiro (`ServireApiApplicationTests`, `FlywayMigrationIntegrationTest`, `TenantIsolationIntegrationTest` — 8 erros), com `NoSuchBeanDefinitionException` para `com.fasterxml.jackson.databind.ObjectMapper`, exigido no construtor de `RestAccessDeniedHandler`. Só os dois testes que não sobem o contexto inteiro (`GlobalExceptionHandlerTest`, `RequestIdFilterTest`) passaram. **Causa raiz:** `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` foram escritas injetando `ObjectMapper` do **Jackson 2**, presumindo (hábito de projetos Spring Boot 3.x) que o Spring registraria esse bean automaticamente. Pesquisado e confirmado em 21/09/2026 (blog oficial do Spring "Introducing Jackson 3 support in Spring" e o guia de migração oficial do Spring Boot 4.0 no GitHub wiki): **a partir do Spring Boot 4, o Jackson padrão da aplicação é o Jackson 3** (pacotes/groupId `tools.jackson.*`, exceto `jackson-annotations` que continua em `com.fasterxml.jackson.annotation`), e a auto-configuration registra um bean `tools.jackson.databind.json.JsonMapper` — nunca mais um `ObjectMapper` do Jackson 2. A classe `ObjectMapper` do Jackson 2 realmente está no classpath (por isso a compilação passa), mas só por causa do `jjwt-jackson` (que ainda não suporta Jackson 3 — https://github.com/jwtk/jjwt/issues/1029), nunca por causa do Spring Web: o projeto tem, de propósito, dois Jacksons coexistindo (Jackson 3 para as respostas HTTP da própria API; Jackson 2 só como motor interno do `jjwt-jackson` para claims de JWT) que nunca deveriam se misturar no código da aplicação. **Correção:** trocar a injeção, nas duas classes, de `com.fasterxml.jackson.databind.ObjectMapper` para `tools.jackson.databind.json.JsonMapper` — o mesmo bean que o Spring Boot 4 já registra e usa para todas as outras respostas da API. Nenhuma outra mudança de código foi necessária (`JsonMapper.writeValue(Writer, Object)` tem a mesma assinatura de conveniência que o `ObjectMapper` do Jackson 2). **✅ Confirmado pelo `mvn clean verify` seguinte do usuário (21/09/2026, 23:37): `BUILD SUCCESS`, 13 testes, 0 falhas, 0 erros.**
>
> **✅ Fase 5 com build real confirmado (21/09/2026, 23:37).** Três execuções reais de `mvn clean verify` rodaram nesta fase, encontrando e corrigindo os bugs reais #4 e #5 acima; a terceira terminou em `BUILD SUCCESS`, confirmando que os 13 testes das Fases 2-4 continuam passando com `spring-boot-starter-security` agora no classpath. A assinatura exata de `CsrfConfigurer.spa()`/`ignoringRequestMatchers(...)` já tinha sido conferida manualmente contra a documentação oficial do Spring Security 7.0.2 antes de escrever `SecurityConfig`, e agora está confirmada também pela compilação/execução real. **Ressalva que permanece:** ainda faltam os testes de integração do próprio fluxo de autenticação (login, seleção de tenant, refresh/rotation, detecção de reuso, logout, Kill Switch, forgot/reset password) — nenhum foi escrito nesta rodada; é o próximo passo recomendado.
>
> **✅ "Task 17" (débito de testes desta fase) paga na Fase 10 (22/09/2026):** `AuthServiceIntegrationTest` (18 métodos) — login em todos os cenários do Kill Switch (e-mail inexistente, senha errada, usuário inativo, tenant bloqueado, único vínculo, múltiplos vínculos pendente de seleção), seleção de tenant (com/sem vínculo), refresh (rotação, reuso de token já revogado revogando todas as sessões — via `ReflectionTestUtils.setField` para forçar expiração sem esperar TTL real —, token expirado, tenant sem vínculo), esqueci senha (e-mail desconhecido não gera token, e-mail conhecido gera), redefinir senha (token válido troca a senha e revoga todos os refresh tokens, token já usado é rejeitado). Os testes de `redefinirSenha` obtêm o token bruto chamando `PasswordResetTokenService.gerar` diretamente (mesma trilha de hash/persistência de produção), não tentando extrair o valor do `LoggingEmailSender` (que só loga, não expõe o valor ao chamador). Ainda **sem confirmação de `mvn clean verify` real** — ver seção 110. A mecânica do cookie `HttpOnly` do refresh token e a isenção de CSRF continuam **sem** cobertura automatizada (esta suíte roda a nível de serviço, não HTTP/`MockMvc`) — risco residual documentado, não fechado nesta rodada.
>
> **Nota (22/09/2026):** uma execução seguinte de `mvn clean verify`, sem o Docker Desktop aberto, terminou em `BUILD FAILURE` com `Could not find a valid Docker environment` nos três testes que sobem o contexto Spring completo via Testcontainers. Isso não é um bug real — é só uma precondição de ambiente (Docker precisa estar rodando para esses testes); não altera a confirmação acima.
>
> **🐛 Bug real #9 (22/09/2026, segurança), encontrado pela primeira rodada de `mvn clean verify` real da Fase 10:** `AuthServiceIntegrationTest.reusoDeRefreshTokenJaRevogadoRevogaTodasAsSessoesDoUsuario` falhou — depois de simular o reuso de um refresh token já revogado (seção 36, cenário de possível roubo de token), a sessão de um segundo dispositivo do mesmo usuário continuava válida, quando deveria ter sido revogada junto. **Causa raiz:** `RefreshTokenService.rotacionar` chama `RefreshTokenRepository.revogarTodosAtivosDoUsuario` (um `UPDATE` em massa) e, na sequência, lança `UnauthorizedException` (uma `RuntimeException`). Pela regra padrão do Spring, uma `RuntimeException` que escapa de um método `@Transactional` marca aquela transação como rollback-only; como `rotacionar` e o método chamador participam da MESMA transação física (propagação padrão `REQUIRED`), o `UPDATE` de revogação — apesar de já enviado ao banco — era desfeito pelo rollback disparado pela exceção lançada logo depois, na mesma transação. A funcionalidade de segurança "reuso de token revoga todas as sessões" nunca funcionou de fato contra um banco real; só parecia correta em revisão de código, porque o `UPDATE` roda antes do `throw` — o efeito só se perde no commit (ou na ausência dele). **Correção:** anotar `RefreshTokenRepository.revogarTodosAtivosDoUsuario` com `@Transactional(propagation = Propagation.REQUIRES_NEW)`, forçando esse `UPDATE` a rodar e COMMITAR numa transação própria e independente, imune ao rollback que a `UnauthorizedException` provoca depois na transação do chamador — funciona porque a chamada passa pelo proxy AOP do próprio repositório (bean diferente de `RefreshTokenService`), contornando a limitação de "self-invocation ignora o proxy". **✅ Confirmado pelo `mvn clean verify` real de 22/09/2026 (terceira e última rodada da batelada da Fase 10): `BUILD SUCCESS`, 75 testes, 0 falhas, 0 erros** — `AuthServiceIntegrationTest.reusoDeRefreshTokenJaRevogadoRevogaTodasAsSessoesDoUsuario` passa, confirmando que a segunda sessão é revogada de verdade. Ver seção 110 e README.md, seção "Bug real #9", para o histórico completo das três rodadas.

---

# 106. FASE 6 — Voluntários

Migrar:

```text
voluntarios
responsaveis
metadata de fotos
```

Angular deixa de acessar essas tabelas diretamente no Supabase.

> **Status (22/09/2026): implementada e com build real confirmado.** Código completo escrito e sincronizado no ambiente do usuário — entidade `Voluntario` expandida de 4 para todas as colunas de V003 (tipo, catequese/eucaristia/crisma, endereço, contato, horário de estudo, observações, autorização WhatsApp, `funcoesHabilitadas`), nova entidade `Responsavel` (V004), `VoluntarioService` (regra de responsável principal único, seção 38, e transação voluntário+responsáveis, seção 39) e `VoluntarioController` com os endpoints `GET/POST/PUT /voluntarios`, `GET /voluntarios/count`, `PATCH /voluntarios/{id}/ativo`, espelhando o `VoluntariosService` do Angular atual (seção 10 do `DOCUMENTACAO-COMPLETA-PARA-IA.md`). **`mvn clean verify` do usuário: `BUILD SUCCESS`, 13 testes, 0 falhas, 0 erros (22/09/2026, 08:36).**
>
> **Decisão explícita do usuário (22/09/2026):** priorizar ter o sistema funcional o quanto antes — os testes de integração desta fase (repositório/isolamento para `Voluntario` expandido e `Responsavel`, seção 80) e os pendentes da Fase 5 (Task 17) ficam deliberadamente adiados. O `BUILD SUCCESS` acima confirma só compilação + contexto Spring subindo + os 13 testes já existentes, não as regras de negócio novas (responsável principal único, substituição de responsáveis) — dívida técnica reconhecida, não esquecida.
>
> **✅ Decisão revertida — débito de testes pago na Fase 10 (22/09/2026), com `BUILD SUCCESS` confirmado (75 testes, 0 falhas, 0 erros, terceira rodada — ver seção 110):** `VoluntarioServiceIntegrationTest` (8 métodos — responsável principal único/não-duplo, "apaga tudo e reinsere" de responsáveis, foto via `StorageService` mockado, `setAtivo`) e `TenantIsolationIntegrationTest.tenantNaoDeveEnxergarResponsavelDeOutroTenant` (isolamento de `Responsavel`, primeiro teste automatizado a provar contra um Postgres real que o `@TenantId` acrescentado pelo bug de revisão de schema abaixo realmente funciona).
>
> **🐛 Bug real #10 (22/09/2026), encontrado na SEGUNDA rodada de `mvn clean verify` real da Fase 10** (depois de corrigidos os Bugs reais #8/#9 e as duas correções de teste da primeira rodada, que até então mascaravam este problema): `VoluntarioServiceIntegrationTest.atualizarSubstituiTodosOsResponsaveisAntigosPelosNovos` falhava com `duplicate key value violates unique constraint "uq_responsavel_principal_por_voluntario"`. **Causa raiz:** `substituirResponsaveis` chama `voluntario.getResponsaveis().clear()`, e o `orphanRemoval = true` agenda os `DELETE`s dos responsáveis antigos — mas isso não obriga o Hibernate a enviá-los ao banco imediatamente. Dentro do mesmo contexto de persistência, o Hibernate podia enviar o `INSERT` do novo responsável `principal = true` ANTES do `DELETE` do antigo (a ordem de flush do Hibernate agrupa operações por tipo, não segue a ordem cronológica da coleção Java) — por um instante, dentro da mesma transação, existiam dois responsáveis `principal = true` para o mesmo `voluntario_id`, e o índice único parcial (V004) recusava corretamente o `INSERT`. **Correção:** um `entityManager.flush()` explícito logo após o `clear()`, forçando a sincronização dos `DELETE`s pendentes com o Postgres antes de qualquer novo `Responsavel` ser adicionado — exige injetar `jakarta.persistence.EntityManager` via `@PersistenceContext` em `VoluntarioService`. **Nota de autoria:** o usuário aplicou essa correção com ajuda de outra IA (sessão anterior já no limite de uso) e confirmou o `BUILD SUCCESS` seguinte; reaplicada aqui com a formatação padrão do projeto. **✅ Confirmado pelo `mvn clean verify` real seguinte (terceira rodada): `BUILD SUCCESS`, 75 testes, 0 falhas, 0 erros** — a Fase 10 + todo o débito de testes das Fases 5-9 estão finalmente confirmados de verdade. O mesmo bug foi corrigido proativamente por analogia em `InscricaoService.substituirResponsaveis` (seção 108) — esse segundo ponto **ainda sem confirmação de build real**, já que nenhum teste automatizado exercita essa troca de principal em `atualizarPendente`.
>
> **Deliberadamente fora do escopo desta primeira versão:** upload real de foto/signed URL (Storage, Fase 7, seção 107 — `foto_path` existe mas nenhum endpoint escreve nele ainda); filtro de listagem por `funcoesHabilitadas` (picker de candidatos, seção 49 — só faz sentido com escalas, Fase 9); endpoint de compromissos (`vw_voluntario_compromissos` — depende de `escalas`/`escala_eventos`/`escala_vagas`, sem entidade JPA até a Fase 9).
>
> **✅ Risco residual confirmado: `funcoesHabilitadas` (array de ENUM nativo do Postgres).** Pesquisado em 21-22/09/2026: `@JdbcTypeCode(SqlTypes.ARRAY)` + `@JdbcType(PostgreSQLEnumJdbcType.class)` tem bug conhecido no Hibernate 7.x (`ClassCastException` em `getJdbcLiteralFormatter`, thread oficial do fórum Hibernate ORM contra 7.2.1.Final: "PostgreSQL array of enums does not work with @JdbcType(PostgreSQLEnumJdbcType.class) but works with @Enumerated + @ColumnTransformer"). Adotada a solução confirmada nessa thread: `@JdbcTypeCode(SqlTypes.ARRAY)` + `@Enumerated(EnumType.STRING)` + `@ColumnTransformer(write = "?::funcao_escala[]")`. Como o projeto usa `ddl-auto: validate`, qualquer incompatibilidade derrubaria o contexto Spring já na subida (mesmo padrão dos bugs reais #1 e #5) — **o `BUILD SUCCESS` de 22/09/2026 confirma que o contexto subiu sem erro de schema/tipo nesta coluna: o mapeamento funciona de verdade contra Hibernate 7.4.5.Final + Postgres real.** Risco encerrado.
>
> **🐛 Bug real encontrado por revisão de schema (não por build), corrigido em 22/09/2026, durante o planejamento conjunto das Fases 7/8/9:** a entidade `Responsavel` desta fase tinha sido escrita olhando só a migration V004 original — sem saber que `V021__add_tenant_id_domain_tables.sql` (Fase 3/4, seção 104, ANTERIOR a esta Fase 6) já tinha acrescentado uma coluna `tenant_id NOT NULL` própria a `responsaveis` (com FK composta tenant-aware para `voluntarios`). A entidade, sem mapear essa coluna, não foi pega pelo `ddl-auto: validate` do build desta fase — esse modo só falha se uma coluna MAPEADA divergir do banco, não se a entidade simplesmente ignorar uma coluna NOT NULL existente — então o `BUILD SUCCESS` de 22/09/2026 08:36 **não** provava que `Responsavel` estava correta; o erro só apareceria em tempo de execução (violação de NOT NULL) no primeiro INSERT real em `responsaveis`. Corrigido adicionando `@TenantId` a `Responsavel`, mesmo padrão já usado em `Voluntario`/`Tenant` — ver README.md e o javadoc da classe para o relato completo.

---

# 107. FASE 7 — Storage

Criar abstração:

```text
FileStorage
```

Implementar:

```text
SupabaseStorage
```

Migrar upload/download para backend.

> **Status (22/09/2026): implementada, AINDA SEM confirmação de `mvn clean verify` real** (implementada junto com as Fases 8 e 9 numa única rodada, por instrução explícita do usuário — "Pode fazer a 7, 8 e 9 de uma vez e depois rodamos o mvn clean verify para ver o build"). `StorageService` (interface, pacote `br.com.servire.api.storage`) + `SupabaseStorageService` (implementação via `RestClient` direto contra a API REST do Supabase Storage — decisão deliberada de não usar o SDK/protocolo S3 completo, seção 15: manter a stack enxuta). Endpoints novos: `POST /voluntarios/{id}/foto` (multipart) e `GET /voluntarios/{id}/foto-url` (URL assinada); `InscricaoService` (Fase 8) reusa o mesmo serviço para a foto do formulário público.
>
> **🐛 Bug real #6 (22/09/2026), confirmado pelo primeiro `mvn clean verify` real depois desta rodada:** `BUILD FAILURE`, 13 testes, 0 falhas, 8 erros — todos os testes que sobem o contexto Spring inteiro falharam com `NoSuchBeanDefinitionException: No qualifying bean of type 'org.springframework.web.client.RestClient$Builder'`. Confirma exatamente o risco já apontado ao escrever `SupabaseStorageService`/`TurnstileService`: diferente do suposto, `spring-boot-starter-web` sozinho NÃO auto-configura esse bean neste projeto (Spring Boot 4.1.1) — mesma categoria de surpresa já vivida com `HibernatePropertiesCustomizer`/`spring-boot-starter-flyway` (Fase 5/6). **Correção:** nova classe `br.com.servire.api.web.RestClientConfiguration`, declarando o bean manualmente (`RestClient.builder()`). **Confirmado corrigido** por um novo `mvn clean verify` real (22/09/2026, 09:24): `BUILD SUCCESS`, 13 testes, 0 falhas, 0 erros.
>
> **⚠️ Riscos residuais a verificar no próximo build real:** (1) o formato exato dos três endpoints da API REST do Supabase Storage usados (`POST .../object/{bucket}/{caminho}` com `x-upsert: true` para upload, `POST .../object/sign/{bucket}/{caminho}` para URL assinada, `DELETE .../object/{bucket}/{caminho}` para excluir) vem da documentação pública, não foi possível confirmar contra um projeto Supabase real neste ambiente de pesquisa. Ver README.md, seção da Fase 7, para o detalhamento completo.
>
> **🐛 Bug real #7 (22/09/2026), encontrado escrevendo `SupabaseStorageServiceTest` (débito de testes desta fase, pago na Fase 10) — NÃO por build:** os três métodos de `SupabaseStorageService` montavam a URI com `.uri(template, bucket, caminho)`, tratando `{caminho}` como UMA única variável de template. Como `caminho` sempre contém `/` de verdade (ex.: `voluntarioId + "/perfil-" + timestamp + extensão`), o `UriComponentsBuilder` por trás do `RestClient` codificava esse `/` como `%2F` ao expandir uma única variável — comportamento padrão e documentado do Spring, mas que teria quebrado TODA chamada real ao Supabase Storage (a API espera segmentos separados por `/` de verdade, não `%2F` literal). **Corrigido** embutindo `bucket`/`caminho` diretamente na string da URI, sem placeholder de template para eles — seguro porque ambos vêm de valores controlados pela própria aplicação (bucket fixo em configuração; caminho montado só com UUID + sufixo fixo + extensão de uma lista permitida). **Ainda não confirmado por um `mvn clean verify` real** — `SupabaseStorageServiceTest` cobre isso com `MockRestServiceServer` (URL literal esperada com barras), mas só um teste contra um Supabase de verdade fecharia esse risco por completo (risco 1 acima continua de pé). Ver README.md, seção "Bug real #7", para o relato completo.
>
> **✅ Débito de testes desta fase pago na Fase 10 (22/09/2026):** `SupabaseStorageServiceTest` (8 métodos, `MockRestServiceServer`) cobre os três endpoints, as validações de arquivo (content-type/tamanho/vazio) e o "falha alto e cedo" de configuração ausente — foi escrevendo esse teste que o Bug real #7 acima foi encontrado.

---

# 108. FASE 8 — Inscrições

Migrar:

- formulário público;
- slug;
- Turnstile;
- rate limit;
- upload;
- aprovação;
- rejeição.

Eliminar Edge Function antiga depois da validação.

> Incluir aqui, quando desenhado com o usuário, o requisito de expiração do link público (ver nota da seção 40 e seção 20.9 do documento técnico). **Ainda não desenhado nesta rodada (22/09/2026)** — descopado desta implementação, fica pendente.

> **Status (22/09/2026): implementada, AINDA SEM confirmação de `mvn clean verify` real** (mesma rodada conjunta das Fases 7/8/9 — ver nota na Fase 7). Novas entidades tenant-aware `Inscricao`/`InscricaoResponsavel` (pacote `br.com.servire.api.inscricao`), espelhando `Voluntario`/`Responsavel` mais os campos de auditoria de aprovação/rejeição (V008). Endpoint público `POST /public/{tenantSlug}/inscricoes` (multipart, sem autenticação — `/public/**` já era `permitAll`/isento de CSRF em `SecurityConfig`, nenhuma mudança necessária lá) protegido por três barreiras em ordem: rate limit por IP em memória (`InscricaoRateLimiter`, 5/hora por padrão, deliberadamente sem Bucket4j/Redis — seção 45), Cloudflare Turnstile (`TurnstileService`, **falha fechado de propósito**: secret vazio, token vazio, chamada HTTP falhando, ou `success: false` — qualquer um desses recusa a inscrição, nunca deixa passar sem validar), e a checagem de Kill Switch do tenant (seção 28) resolvido pelo slug. Fila de aprovação autenticada em `InscricaoController` (`GET/PUT /inscricoes`, `POST /{id}/aprovar` cria o `Voluntario` de verdade + copia responsáveis, `POST /{id}/rejeitar` exige motivo).
>
> Como o formulário público não passa por `JwtAuthenticationFilter`, `InscricaoService.criarPublica` é a única classe de negócio do projeto que chama `TenantContext.set`/`clear` diretamente (com `try/finally`, regra P0 da seção 20/78-80), imitando o que o filtro faz para requisições autenticadas.
>
> **⚠️ Riscos residuais a verificar no próximo build real:** (1) o rate limit lê `X-Forwarded-For` antes de cair para `getRemoteAddr()` — só confiável se o reverse proxy do VPS de produção (Nginx/Caddy, seção 11/89) REESCREVER esse header, não confirmado nesta rodada; (2) o formato da resposta do Cloudflare Turnstile (lida como `Map<String,Object>` olhando só `success`, para não depender de anotação de serialização específica) nunca foi testado contra o Cloudflare de verdade; (3) ~~nenhum teste automatizado cobre o fluxo completo~~ — **pago na Fase 10** (ver abaixo). O Bug real #6 (`RestClient.Builder` ausente, ver seção 107) também derrubava `TurnstileService` desta fase — corrigido pela mesma `RestClientConfiguration` e confirmado pelo `mvn clean verify` de 22/09/2026 09:24 (`BUILD SUCCESS`, 0 erros). Ver README.md, seção da Fase 8, para detalhes.
>
> **✅ Débito de testes desta fase pago na Fase 10 (22/09/2026):** `TurnstileServiceTest` (6 métodos, `MockRestServiceServer` — fail-closed em todos os cenários: secret ausente, token vazio, `success: false`, falha HTTP, IP nulo), `InscricaoRateLimiterTest` (4 métodos, unitário puro — janela deslizante por IP, contadores independentes por IP) e `InscricaoServiceIntegrationTest` (10 métodos — rate limit como primeira barreira, slug inexistente/tenant bloqueado com a mesma mensagem genérica anti-enumeração, exatamente um responsável principal, aprovar cria `Voluntario` real + copia responsáveis, rejeitar exige motivo, atualizar pendente bloqueado depois de aprovada). Os riscos 1 e 2 acima continuam **não verificados** — nenhum teste automatizado fecha esses dois sem um ambiente de produção/Cloudflare de verdade.
>
> **🐛 Bug real #8 (22/09/2026), encontrado pela primeira rodada de `mvn clean verify` real da Fase 10:** os testes de `InscricaoServiceIntegrationTest` que exercitam `criarPublica` falhavam com violação da FK `inscricoes_tenant_id_fkey` — a inscrição pública nunca conseguia ser gravada de fato num banco real, apesar de todo o código parecer correto em revisão manual. **Causa raiz:** o Hibernate resolve e FIXA o identificador de tenant de uma sessão no momento em que a sessão é aberta — e sob `@Transactional` declarativo do Spring, a sessão é aberta na ENTRADA do método (pelo proxy AOP), antes do corpo do método rodar. `criarPublica` tinha `@Transactional` no método inteiro, mas só chamava `TenantContext.set(tenant.getId())` DENTRO do corpo, depois de resolver o tenant pelo slug — tarde demais: a sessão já tinha nascido presa ao tenant sentinela `SEM_TENANT`. O `INSERT` em `inscricoes`, adiado até o commit (que só acontece depois que o corpo do método — incluindo o `finally` que limpa o `TenantContext` — já terminou), sempre tentava gravar `tenant_id = SEM_TENANT`, violando a FK. **Isso significa que a funcionalidade carro-chefe desta fase nunca funcionou de fato contra um banco real.** Esse é exatamente o mesmo mecanismo (sessão do Hibernate resolve o tenant uma única vez, na abertura) já identificado e corrigido proativamente em dois métodos de `TenantIsolationIntegrationTest` durante a própria revisão manual desta rodada (removendo `@Transactional` deles, seção 110) — faltava aplicar a mesma lição ao código de produção. **Correção:** removido `@Transactional` do método `criarPublica`; um novo `TransactionTemplate` (construído a partir de um `PlatformTransactionManager` injetado no construtor) abre a transação manualmente, DEPOIS de `TenantContext.set(...)` já ter rodado — a lógica de persistência foi extraída para um novo método privado `gravarInscricaoPublica`, chamado dentro de `transactionTemplate.execute(...)`, com o `try/finally` de limpeza de `TenantContext`/MDC continuando a envolver tudo. **✅ Confirmado pelo `mvn clean verify` real seguinte (terceira e última rodada da batelada): `BUILD SUCCESS`, 75 testes, 0 falhas, 0 erros** — `InscricaoServiceIntegrationTest` passa 10/10, incluindo os métodos que exercitam `criarPublica` de ponta a ponta. Ver seção 110 e README.md, seção "Bug real #8", para o histórico completo.
>
> **🐛 Bug real #10 (mesmo mecanismo, seção 106) também corrigido proativamente em `InscricaoService`:** o `substituirResponsaveis` de `InscricaoService` (usado por `atualizarPendente` contra uma inscrição PENDENTE já existente) tem o mesmo padrão "apaga tudo e reinsere" que causou o Bug real #10 em `VoluntarioService` — e `inscricao_responsaveis` tem o mesmo tipo de índice único parcial (`ux_inscricao_responsavel_principal`, V009) que causaria o mesmo `duplicate key` ao trocar o responsável principal de uma inscrição existente. Corrigido por analogia, com o mesmo `entityManager.flush()` logo após o `clear()` — **mas sem confirmação de build real**, já que nenhum teste automatizado exercita essa troca de principal em `atualizarPendente`.

---

# 109. FASE 9 — Escalas

Migrar:

- escala;
- eventos;
- vagas;
- candidatos;
- status;
- exportação.

Corrigir inconsistências identificadas.

> **Nova funcionalidade a incluir nesta fase (21/09/2026, seção 122 itens 11 e 12, ver 131.5):** controle de faltas e disponibilidade do voluntário. **Descoparia desta rodada (22/09/2026)** — não foi desenhada a tempo junto com o resto da Fase 9; fica para uma próxima iteração, com entidades/campos próprios ainda a definir (ver README.md, "Próximos passos").

> **Status (22/09/2026): implementada, AINDA SEM confirmação de `mvn clean verify` real** (mesma rodada conjunta das Fases 7/8/9 — ver nota na Fase 7). Novas entidades tenant-aware `Escala`/`EscalaEvento`/`EscalaVaga` (pacote `br.com.servire.api.escala`), reaproveitando `FuncaoEscala` da Fase 6. Comportamento "apaga todos os eventos/vagas e reinsere" a cada save PRESERVADO deliberadamente (mesmo padrão do Angular atual, já documentado desde V006) via `clear()` + reinserção na coleção gerenciada pelo Hibernate — nunca SQL nativo (Native Query Gate, seção 81). Transições de estado: finalizar (só de RASCUNHO), cancelar (de RASCUNHO/FINALIZADA), reabrir (de FINALIZADA/CANCELADA, volta a RASCUNHO), excluir (só se CANCELADA — regra explícita desta seção). Edição (`PUT`) só permitida em RASCUNHO.
>
> **Controle otimista (seção 47) implementado:** `Escala.version` (`@Version`) — coluna nova, não existia antes desta rodada. `EscalaService.atualizar` faz uma checagem explícita de versão (payload vs. banco) ANTES de mudar qualquer coisa, devolvendo 409 com "A escala foi alterada por outro usuário. Atualize a página."; o `@Version` do Hibernate fica como rede de segurança contra uma corrida de verdade entre requisições concorrentes (`GlobalExceptionHandler` ganhou um handler para `ObjectOptimisticLockingFailureException` com a mesma mensagem).
>
> **Nova migration `V023__ajustes_fases_7_8_9.sql`** (schema não mudava desde V022): repontou as FKs `escalas.created_by`/`inscricoes.aprovado_por`/`inscricoes.rejeitado_por`, que ainda apontavam para `auth.users` do Supabase Auth (em remoção progressiva, seção 32/83), para `public.usuario` (identidade de ator própria da aplicação desde a Fase 5) — sem essa correção, gravar um `usuario.id` nessas colunas falharia a FK em tempo de execução no primeiro "criar escala"/"aprovar"/"rejeitar" via JWT. Somou também `escalas.version` para o controle otimista acima. `FlywayMigrationIntegrationTest` atualizado de 22 para 23 migrations.
>
> **⚠️ Riscos residuais a verificar no próximo build real:** (1) ~~controle otimista nunca testado contra um Postgres real~~ — **pago na Fase 10** por `EscalaServiceIntegrationTest.atualizarComVersaoDivergenteLancaConflictException` (checagem explícita de `EscalaService`) e `GlobalExceptionHandlerTest.objectOptimisticLockingFailureExceptionViraHttp409ComMensagemDeNegocio` (rede de segurança do Hibernate); (2) ~~cascata tenant-aware de três níveis nunca confirmada rodando~~ — **pago na Fase 10** por `TenantIsolationIntegrationTest.tenantNaoDeveEnxergarEscalaComCascataDeTresNiveisDeOutroTenant` (isolamento nos três níveis: `Escala` via repositório, `EscalaEvento`/`EscalaVaga` via JPQL direto); (3) ~~nenhum teste automatizado cobre "apaga tudo e reinsere", transições de estado, ou controle otimista~~ — **pago na Fase 10** por `EscalaServiceIntegrationTest` (6 métodos: criação em cascata, substituição de eventos, versão divergente, atualizar fora de RASCUNHO, mesmo voluntário em duas vagas do mesmo evento, matriz de transição de estado completa). **Os três itens agora estão ✅ confirmados pelo `BUILD SUCCESS` de 75 testes, 0 falhas, 0 erros** (terceira rodada da batelada da Fase 10 — ver seção 110). Ver README.md, seção da Fase 9, para detalhes.

---

# 110. FASE 10 — Multi-tenant real

Até aqui a infraestrutura multi-tenant já estará presente.

Nesta fase:

1. criar segundo tenant de teste;
2. duplicar massa mínima;
3. validar isolamento;
4. executar suíte P0;
5. ajustar índices;
6. validar Storage;
7. validar inscrição pública por slug.

Não existe criação de outro banco.

> **CONFIRMADO em 21/09/2026:** a leitura da seção 131.1 foi confirmada explicitamente pelo usuário — um mesmo usuário realmente vai operar múltiplas paróquias já no MVP, trocando entre elas via um seletor sempre visível no topo da tela (ver seção 30). Esta fase passa definitivamente de "validação de arquitetura com tenant de teste" para "fluxo real de produto", e deve incluir como entregável a UI de seleção/troca de tenant (seção 30), não só o teste técnico de isolamento.

> **Status (22/09/2026): ✅ parte técnica (testes de isolamento) implementada e CONFIRMADA com `mvn clean verify` real: `BUILD SUCCESS`, 75 testes, 0 falhas, 0 erros, reconfirmado numa quarta rodada independente** — feita junto com todo o débito de testes pendente das Fases 5-9, na mesma rodada, por instrução explícita do usuário. Foram necessárias quatro rodadas reais de build (histórico completo abaixo). A UI de seleção/troca de tenant do parágrafo acima continua fora do escopo deste repositório (backend Java) — item do frontend Angular, não endereçado aqui.
>
> **Histórico das quatro rodadas reais desta batelada:**
> 1. 🔴 **Primeira rodada (22/09/2026): `BUILD FAILURE`** — `Tests run: 75, Failures: 2, Errors: 14`. A compilação passou e o contexto Spring subiu normalmente (os 14 erros e 2 falhas foram todos em tempo de execução/asserção) — analisado o log completo enviado pelo usuário (`erros.md`), foram encontrados e corrigidos quatro problemas, todos batendo exatamente com a contagem do log: (a) 12 erros — `codigo` de tenant fixo (não randomizado como o `slug`) em `VoluntarioServiceIntegrationTest`/`EscalaServiceIntegrationTest`, colidindo com a `UNIQUE constraint` a partir do segundo método de teste de cada classe; (b) 2 erros — **Bug real #8** (`InscricaoService.criarPublica`, seção 108); (c) 1 falha — **Bug real #9** (detecção de reuso de refresh token, seção 105); (d) 1 falha — `InscricaoRateLimiterTest` usando `Duration.ofNanos(1)` para simular janela expirada, suposição de resolução de relógio que não se confirmou na máquina Windows do usuário.
> 2. 🔴 **Segunda rodada, depois das quatro correções acima: `BUILD FAILURE`** — `Tests run: 75, Failures: 0, Errors: 1`. Só sobrou um erro — a correção do `codigo` duplicado parou de mascarar os outros 7 métodos de `VoluntarioServiceIntegrationTest`, revelando um bug real novo: **Bug real #10** (`VoluntarioService.substituirResponsaveis`, seção 106) — `INSERT` do novo responsável principal podia chegar ao banco antes do `DELETE` do antigo, violando o índice único parcial de responsável principal.
> 3. ✅ **Terceira rodada, depois da correção do Bug real #10 (aplicada pelo usuário com ajuda de outra IA): `BUILD SUCCESS`, `Tests run: 75, Failures: 0, Errors: 0`.** Toda a Fase 10 + o débito de testes das Fases 5-9 confirmados de verdade contra um Postgres real.
> 4. ✅ **Quarta rodada (22/09/2026, 13:15) — reconfirmação independente, direto do terminal do usuário: `BUILD SUCCESS`, `Tests run: 75, Failures: 0, Errors: 0`.** Já roda com a correção do Bug real #10 reformatada no padrão do projeto e com o fix proativo em `InscricaoService.substituirResponsaveis` — confirmando que nenhuma das duas mudanças quebrou nada.
>
> **Os 7 itens desta seção e como cada um foi endereçado:**
> 1. **Criar segundo tenant de teste / duplicar massa mínima** — cada método de teste nas classes novas (`TenantIsolationIntegrationTest`, `VoluntarioServiceIntegrationTest`, `InscricaoServiceIntegrationTest`, `EscalaServiceIntegrationTest`, `AuthServiceIntegrationTest`) cria seu próprio tenant descartável via `TenantRepository.saveAndFlush` — não existe um tenant "B" fixo compartilhado entre testes, para não contaminar um teste com dado de outro (nenhuma dessas classes usa rollback automático por método).
> 2. **Validar isolamento** — `TenantIsolationIntegrationTest` ganhou três métodos novos: isolamento de `Responsavel` (cascata de 1 nível a partir de `Voluntario`, primeira confirmação automatizada de que o `@TenantId` acrescentado pelo bug real de revisão de schema da Fase 6/9 realmente funciona), de `Escala`→`EscalaEvento`→`EscalaVaga` (cascata de 3 níveis — os dois níveis mais profundos, sem repositório dedicado, checados via JPQL direto por `EntityManager`) e de `Inscricao`+`InscricaoResponsavel` (cascata de 1 nível). Somados ao teste de `Voluntario` já existente desde a Fase 4, agora as sete entidades tenant-aware do projeto têm cobertura de isolamento própria — ✅ **confirmado**, `TenantIsolationIntegrationTest` passa 5/5.
> 3. **Executar suíte P0** — ✅ **a suíte ampliada já rodou de fato contra o Postgres real do usuário e passou 5/5**, confirmando inclusive que a correção proativa de remover `@Transactional` de dois dos seus métodos (feita durante a própria revisão manual desta rodada, por causa do mesmo mecanismo de resolução de tenant por sessão do Hibernate que depois também explicou o Bug real #8) estava certa.
> 4. **Ajustar índices** — **nenhuma migration nova necessária.** A V021 (Fase 3/4, seção 104) já criou os índices compostos `(tenant_id, ...)` das sete tabelas de domínio junto com a própria coluna `tenant_id`, duas fases atrás — não era um item pendente até agora.
> 5. **Validar Storage** — ✅ **confirmado** — `SupabaseStorageServiceTest` (débito de testes da Fase 7, ver seção 107) cobre os três endpoints, passa 8/8; foi escrevendo esse teste que o Bug real #7 foi encontrado e corrigido. Formato real da API Supabase continua não confirmado contra um projeto de verdade (único risco que nenhum teste automatizado fecha).
> 6. **Validar inscrição pública por slug** — ✅ **confirmado** — coberto indiretamente pelo teste de isolamento de `Inscricao` (item 2: dois tenants com slugs distintos, cada um só enxergando a própria inscrição) e pelos testes de `InscricaoServiceIntegrationTest` que exercitam `criarPublica` resolvendo pelo slug, incluindo slug inexistente e tenant bloqueado (mesma mensagem genérica anti-enumeração) — passa 10/10.
>
> **Débito de testes de outras fases pago na mesma rodada** (fora do escopo formal da seção 110, mas feito junto por instrução do usuário — "aí fazemos todos os testes pendentes"): `AuthServiceIntegrationTest` (Fase 5, ver seção 105), `VoluntarioServiceIntegrationTest` (Fase 6, ver seção 106), `TurnstileServiceTest`/`InscricaoRateLimiterTest`/`InscricaoServiceIntegrationTest` (Fase 8, ver seção 108), `EscalaServiceIntegrationTest` + um teste novo em `GlobalExceptionHandlerTest` (Fase 9, ver seção 109).
>
> **✅ Com o `BUILD SUCCESS` de 75 testes confirmado, esta rodada pode ser tratada como "pronta"** no sentido de que compila e as regras de negócio de cada fase funcionam contra um Postgres real — a única exceção é o fix proativo (por analogia ao Bug real #10) em `InscricaoService.substituirResponsaveis` (seção 108), que continua sem um teste automatizado que o exercite. Os demais riscos residuais que continuam em aberto (formato real das APIs do Supabase Storage e do Cloudflare Turnstile, configuração real do reverse proxy de produção) são os que nenhum teste automatizado consegue fechar sozinho, documentados nas seções de cada fase. Ver README.md, seção "Fase 10", para o detalhamento completo.

---

# 111. FASE 11 — Backoffice

Criar:

```text
admin frontend
+
admin APIs
```

MVP:

- dashboard;
- tenants;
- usuários;
- status;
- logs;
- bloqueio.

> **Nota (21/09/2026):** como a decisão da seção 62/131.3 é operar com PIX manual, o backoffice do MVP precisa incluir uma ação simples de "marcar assinatura como paga/liberar acesso manualmente", já que não haverá webhook de gateway automatizando isso no início.

---

# 112. FASE 12 — Billing

Implementar:

- planos;
- preços;
- assinatura;
- cobranças;
- webhooks;
- bloqueio.

> **Nota (21/09/2026):** com PIX manual (131.3), "cobranças" e "webhooks" ficam simplificados para um MVP inicial: sem integração de gateway, a ação de registrar pagamento é manual no backoffice (ver nota da FASE 11). A estrutura de dados (`plano`, `preco_plano`, `assinatura`, `cobranca`) continua válida e deve ser criada do jeito descrito na seção 63, só a automação de cobrança fica para depois.

---

# 113. FASE 13 — Deploy MVP

Frontend:

```text
Cloudflare Pages
```

Backend:

```text
VPS + Docker
```

Banco/Storage:

```text
Supabase Free
```

Configurar:

- domínio;
- TLS;
- CORS;
- backups;
- logs;
- healthcheck.

> Domínio a configurar: `servirea.com.br` (ver seção 12 e 131.8), uma vez registrado.

---

# 114. FASE 14 — Migração dos dados atuais

Fluxo:

```text
extract
transform
adicionar tenant_id
load
validate
```

Scripts devem ser idempotentes quando possível.

Todos os dados existentes deverão ser atribuídos ao tenant inicial correto.

> **Nota (21/09/2026):** o backfill do `tenant_id` nas 7 tabelas de domínio já foi antecipado na Fase 3 (`V021__add_tenant_id_domain_tables.sql`, ver seção 103), atribuindo tudo ao tenant seed criado na `V020`. Esta fase, quando chegar sua vez cronológica, cobre a migração de dados que ainda vierem de fora (ex.: dados reais de produção do Supabase atual, se diferentes do que já está no baseline), não repete o que já foi feito aqui.

---

# 115. FASE 15 — Remoção do Supabase direto do Angular

Somente quando:

```text
100% APIs necessárias migradas
auth migrado
uploads migrados
RPCs migradas
Edge Functions migradas
dados conferidos
```

Então remover progressivamente:

- acesso de tabela Supabase no Angular;
- Supabase Auth;
- RPCs de negócio;
- Edge Functions antigas.

Manter:

- PostgreSQL Supabase;
- Storage Supabase.

---

# 116. Definition of Done do SaaS MVP

O MVP estará tecnicamente pronto quando:

- tenant A nunca acessar tenant B;
- relações cross-tenant forem bloqueadas;
- login próprio funcionar;
- refresh rotation funcionar;
- kill switch funcionar;
- voluntários funcionarem;
- responsáveis funcionarem;
- inscrição pública funcionar;
- aprovação/rejeição funcionarem;
- escalas funcionarem;
- arquivos forem privados;
- arquivos de outro tenant forem inacessíveis;
- migrations forem reproduzíveis;
- backup tiver sido restaurado em teste;
- deploy funcionar;
- logs estruturados existirem;
- billing funcionar quando entrar no escopo comercial;
- auditoria existir.

---

# 117. Prioridades de segurança

## P0

```text
tenant isolation
auth
JWT
refresh token
constraints tenant-aware
SQL nativo
uploads
transações
secrets
backup
Turnstile
```

## P1

```text
auditoria
rate limit
observabilidade
concorrência
headers
hardening VPS
```

## P2

```text
performance
cache
otimizações
infraestrutura distribuída
```

---

# 118. O que NÃO fazer

## Não criar um banco por paróquia no MVP

Decisão atual:

```text
tenant_id
```

---

## Não manter acesso direto do Angular ao banco como arquitetura final

Angular deverá consumir Spring Boot.

---

## Não reescrever Angular sem necessidade

O Angular atual pode evoluir.

---

## Não implementar AWS prematuramente

AWS continua sendo opção futura.

Não é requisito do MVP.

---

## Não implementar Redis sem necessidade

---

## Não implementar Kubernetes

---

## Não confiar apenas em @TenantId

Também usar:

- autorização;
- constraints;
- FKs tenant-aware;
- testes;
- regras para SQL nativo.

---

## Não usar tenant_id vindo do body como autoridade

---

## Não permitir SQL nativo indiscriminado

---

## Não armazenar secrets no frontend

---

## Não expor Supabase server credentials

---

## Não migrar bugs como regras de negócio

Divergências precisam ser documentadas antes de decidir.

---

# 119. Decisões arquiteturais consolidadas

## Identidade técnica

```text
servire-web
servire-api
servire-admin
servire-mobile
```

Os nomes acima deverão ser usados preferencialmente em repositórios, imagens Docker, pipelines e artefatos relacionados ao produto.

## Frontend

```text
Angular
Cloudflare Pages
```

## Backend

```text
Java 21
Spring Boot 4.1.x
Spring MVC
Virtual Threads
Docker
VPS ICP CORE
```

> **Atualização (21/09/2026):** esta seção documentava originalmente "Spring Boot 3". Ao iniciar a FASE 2 (seção 102/125), pesquisei e confirmei que a linha 3.5 (última do Spring Boot 3) chegou ao fim do suporte open-source em 30/06/2026 — a linha atualmente suportada é a 4.1.x (versão corrente: 4.1.1, agosto/2026). Perguntei ao usuário, que confirmou: começar já em Spring Boot 4.1.x. Isso implica Jakarta EE 11 e Spring Framework 7 — impacto prático baixo, já que o projeto nasce usando `jakarta.*` (convenção que já valia desde o Boot 3) e ainda não tem módulos de negócio migrados. Ver seção 102 para o status da fundação já entregue nessa versão.

## ORM

```text
Hibernate 6
Spring Data JPA
```

## Multi-tenancy

```text
Shared Database
Shared Schema
tenant_id
Hibernate @TenantId
```

## Banco

```text
Supabase PostgreSQL
```

## Migration

```text
Flyway
```

## Auth

```text
Spring Security
JWT
Refresh Token Rotation
```

## Arquivos

```text
Supabase Storage
interface FileStorage
```

## Infraestrutura inicial

```text
Cloudflare Pages
+
VPS
+
Supabase Free
```

## Containers

```text
Docker Compose
```

---

# 120. Decisões que mudaram em relação ao Plano Mestre anterior

| Tema | Antes | Agora |
|---|---|---|
| Multi-tenancy | Database-per-Tenant | Shared DB + `tenant_id` |
| Master | banco `acl_simp` separado | tabelas globais no mesmo PostgreSQL |
| Datasource | pool/datasource por tenant | único datasource |
| Tenant routing | `AbstractRoutingDataSource` | `@TenantId` + resolver |
| Provisionamento | criar banco por cliente | inserir novo `tenant` |
| Flyway | migrar N bancos | migrar um único banco |
| Frontend hosting | AWS S3 + CloudFront | Cloudflare Pages |
| Backend hosting | AWS ECS/Fargate | VPS + Docker |
| PostgreSQL | RDS/Aurora futuro imediato | Supabase Free |
| Storage | AWS S3 | Supabase Storage |
| Secrets | AWS Secrets Manager/SSM | secret seguro no VPS no MVP |
| Infra | AWS completa | arquitetura mínima |
| Custo fixo | infraestrutura AWS variável | VPS + tiers gratuitos |

---

# 121. Melhorias incorporadas nesta versão

Além da mudança de infraestrutura, esta versão incorpora as seguintes decisões:

1. Master lógico criado desde a autenticação;
2. usuário preparado desde cedo para múltiplas paróquias;
3. UUIDs globais;
4. refresh token rotation;
5. token armazenado somente por hash;
6. permissions além de roles;
7. foreign keys tenant-aware;
8. unique constraints tenant-aware;
9. regra formal para SQL nativo;
10. testes negativos de isolamento;
11. FileStorage desacoplado do fornecedor;
12. object key com UUID;
13. estratégia TEMP/ACTIVE para Storage;
14. auditoria sem duplicação excessiva de PII;
15. Flyway com baseline;
16. migrations expand/migrate/contract;
17. backup externo obrigatório;
18. gatilhos objetivos para upgrade;
19. separação entre operador SaaS e usuário de paróquia;
20. Cloudflare Pages para eliminar custo do frontend.

---

# 122. Pontos que precisavam de decisão de produto — RESPONDIDO em 21/09/2026

Todas as 18 perguntas abaixo foram respondidas pelo usuário em 21/09/2026. O detalhamento completo de cada resposta, incluindo recomendações dadas para os itens 17 e 18 (onde o usuário pediu sugestão e depois confirmou adotá-las), está na **seção 131**. Este bloco permanece como registro histórico das perguntas originais.

1. Um usuário realmente usará múltiplas paróquias no MVP ou apenas a arquitetura permitirá? → **Respondido e CONFIRMADO — ver 131.1. Sim, uso real, com troca de paróquia ativa via seletor no topo da tela.**
2. Padre terá role própria? → **Respondido — ver 131.2. Não.**
3. Existe limite de voluntários por plano? → **Respondido — ver 131.3. Sim.**
4. Quais planos existirão? → **Respondido — ver 131.3. "Standard" e "Pro", mensal e anual.**
5. Haverá trial? → **Respondido — ver 131.3. Sim, 7 dias.**
6. Quantos dias de inadimplência antes do bloqueio? → **Respondido — ver 131.3. 3 dias.**
7. `BLOQUEADO` permitirá somente leitura? → **Respondido — ver 131.4. Não, bloqueio total.**
8. Haverá WhatsApp? → **Respondido — ver 131.5. Não, por enquanto.**
9. Haverá aplicativo mobile? → **Respondido — ver 131.5. Não, só site responsivo por enquanto.**
10. Haverá confirmação de presença? → **Respondido — ver 131.5. Não, por enquanto.**
11. Haverá controle de faltas? → **Respondido — ver 131.5. Sim.**
12. Haverá disponibilidade do voluntário? → **Respondido — ver 131.5. Sim.**
13. Responsável terá portal próprio? → **Respondido — ver 131.6. Não, por enquanto.**
14. Haverá escala pública? → **Respondido — ver 131.6. Sim, protegida por chave secreta cadastrada pelo coordenador.**
15. Qual gateway de pagamento será o primeiro? → **Respondido — ver 131.3. Nenhum por enquanto — PIX manual.**
16. Qual domínio oficial do produto? → **Respondido — ver 131.8. `servirea.com.br` (ainda não registrado).**
17. Qual política de retenção LGPD? → **Respondido e CONFIRMADO — ver 131.7. Usuário adotou integralmente a recomendação do assistente.**
18. Qual destino definitivo dos backups externos? → **Respondido e CONFIRMADO — ver 131.9. Usuário decidiu por Cloudflare R2.**

---

# 123. Instruções para IA/desenvolvedor que continuar o projeto

1. Usar este documento como direção arquitetural atual.
2. Não voltar para database-per-tenant sem nova decisão formal.
3. Não assumir que README antigo representa produção.
4. Consultar dump/schema real antes de modificar banco.
5. Toda entidade de domínio deve ser classificada como global ou tenant-aware.
6. Entidade tenant-aware deve possuir `tenant_id`.
7. Usar `@TenantId` nas entidades apropriadas.
8. Não confiar que `@TenantId` protege native SQL.
9. Toda native query tenant-aware deve filtrar explicitamente por tenant.
10. Nunca aceitar `tenant_id` do body como autoridade.
11. Tenant de endpoint autenticado deriva do contexto autorizado.
12. Tenant de endpoint público deriva de slug validado.
13. Toda relação tenant-aware deve impedir referência cross-tenant.
14. Toda operação multi-entidade SQL deve ser transacional.
15. Toda API valida autorização no backend.
16. Não confiar em filtro Angular.
17. Toda mudança estrutural passa por Flyway.
18. Nunca colocar secret no frontend.
19. Nunca expor credencial server-side do Supabase.
20. Arquivo privado só é retornado após autorização.
21. Sempre escrever testes de isolamento.
22. Preservar regras antes de otimizar.
23. Divergências antigas devem ser documentadas antes de corrigir.
24. Infraestrutura deve permanecer simples enquanto métricas não justificarem complexidade.
25. Custos devem permanecer baixos até existir tração que justifique upgrade.
26. **As 18 decisões da seção 122 já foram respondidas e confirmadas pelo usuário (seção 131) — tratá-las como decisões vigentes. Os únicos pontos ainda em aberto são valores exatos (limites de voluntário por plano e preços em R$, seção 131.3), que não bloqueiam o desenho arquitetural.**

---

# 124. Primeira tarefa recomendada

A primeira tarefa continua não sendo criar controllers.

Ela será:

```text
Reconstruir e versionar o banco atual.
```

Passos:

1. obter dump de produção;
2. listar ENUMs;
3. listar tabelas;
4. listar constraints;
5. listar foreign keys;
6. listar indexes;
7. listar triggers;
8. listar functions;
9. listar RPCs;
10. listar policies;
11. documentar buckets e Storage;
12. gerar schema canônico;
13. definir baseline Flyway;
14. subir PostgreSQL limpo;
15. executar migrations;
16. comparar schema;
17. validar equivalência.

Somente depois iniciar migração funcional para Java.

> **Status (21/09/2026): passos 1 a 17 concluídos — Fase 0 e Fase 1 encerradas.** Os passos 12-17 foram feitos nesta sessão: 15 migrations Flyway (`V001`-`V015`) reconstruindo o schema real (ENUMs, tabelas, view, RPCs, grants, RLS), validadas rodando de fato num PostgreSQL 16 limpo — todas as asserções estruturais e um teste funcional ponta a ponta (criar → aprovar inscrição, simulando `anon`/`authenticated`/`service_role`) passaram. Entregue ao usuário como `servire-database-baseline.zip` (ver seção 101 para o detalhamento e as duas ressalvas de "reconstruído por inferência, não extraído literalmente" que ainda merecem confirmação ao vivo no painel). Próximo passo recomendado: seção 125 (FASE 2 — fundação Spring Boot), usando este mesmo diretório `migrations/` como fonte do Flyway do backend.

---

# 125. Segunda tarefa recomendada

Após o banco reproduzível:

```text
Criar a fundação Spring Boot.
```

Incluindo:

- Java 21;
- Spring Boot;
- Spring MVC;
- Virtual Threads;
- Spring Data JPA;
- Hibernate;
- Flyway;
- Testcontainers;
- `/health`;
- exception handling;
- logs estruturados;
- request ID.

Ainda sem tentar migrar todos os módulos.

---

# 126. Terceira tarefa recomendada

Criar o modelo SaaS lógico:

```text
tenant
usuario
usuario_tenant
refresh_token
```

Adicionar:

```text
tenant_id
```

às entidades de domínio.

Criar TenantContext e integração Hibernate.

Escrever os primeiros testes críticos de isolamento antes de expor dados reais de múltiplas paróquias.

> **Status (21/09/2026): concluída, com build verificado de verdade — ver seções 103 e 104 para o detalhamento completo.** Resumo: as 4 tabelas do modelo SaaS lógico existem (com JPA completo só para `tenant`/`usuario` por enquanto na época; `usuario_tenant`/`refresh_token` ganharam entidade JPA na Fase 5, seção 105); `tenant_id` foi adicionado às 7 tabelas de domínio com backfill e FKs compostas tenant-aware; `TenantContext`/`CurrentTenantIdentifierResolver`/`@TenantId` estão implementados e ligados ao Hibernate; existe uma primeira entidade tenant-aware (`Voluntario`, minimalista) e um teste crítico de isolamento cobrindo o cenário P0 da seção 78. Rodando `mvn clean verify` de verdade, o usuário ajudou a encontrar e confirmar a correção de três bugs reais (resolver derrubando o bootstrap do Spring; contagem de migrations desatualizada; container Testcontainers não era realmente singleton — ver seção 104 para o detalhamento dos três). **Confirmação final recebida: `BUILD SUCCESS`, 13 testes, 0 falhas, 0 erros** — nenhuma ressalva pendente. Próximo passo recomendado (e já em andamento): Fase 5 (seção 105 — autenticação própria).

---

# 127. Resultado esperado ao final do MVP

```text
Usuário
  ↓
Cloudflare Pages / Angular
  ↓
HTTPS
  ↓
VPS / Spring Boot
  ↓
TenantContext
  ↓
Hibernate @TenantId
  ↓
Supabase PostgreSQL
```

Dados:

```text
Tenant A ─┐
Tenant B ─┼── mesmo PostgreSQL
Tenant C ─┘
           ↓
        tenant_id
```

Arquivos:

```text
tenant/A/...
tenant/B/...
tenant/C/...
```

Controle:

```text
tenant
usuario
usuario_tenant
assinatura
```

Tudo no mesmo banco físico, com separação lógica e regras de isolamento obrigatórias.

---

# 128. Filosofia de evolução

A prioridade do projeto será:

```text
segurança
+
integridade
+
baixo custo
+
simplicidade operacional
```

antes de:

```text
infraestrutura sofisticada
```

A arquitetura deverá crescer junto com a receita e com requisitos reais.

Não será criada complexidade operacional para um volume de clientes que ainda não existe.

Ao mesmo tempo, decisões fundamentais — especialmente isolamento de tenant, autenticação, migrations, backup e proteção dos dados de menores — não serão adiadas.

---

# 129. Referências técnicas verificadas nesta revisão

Consultadas em 21/09/2026:

- Supabase Pricing — limites do plano Free.
- Supabase Billing Docs — projetos Free e quotas.
- Supabase Database Size Docs — limite de database size.
- Supabase Storage Docs — Storage e limites.
- Supabase S3 Compatibility / Authentication — acesso server-side compatível com S3.
- Hibernate ORM 6 — `@TenantId` e `CurrentTenantIdentifierResolver`.
- Hibernate ORM Introduction — discriminator-based multi-tenancy e alerta sobre native SQL.
- Cloudflare Pages Docs — limites e precificação de assets estáticos.
- Spring Security `CsrfConfigurer` (spring-security-docs 7.0.2 API) — confirmação do método `spa()` (desde a versão 7.0) e de `ignoringRequestMatchers(String...)`, consultada em 21/09/2026 ao escrever `SecurityConfig` da Fase 5.
- Spring blog "Introducing Jackson 3 support in Spring" e o "Spring Boot 4.0 Migration Guide" (GitHub wiki, spring-projects/spring-boot) — confirmação de que o Spring Boot 4 migrou para Jackson 3 (`tools.jackson.*`) como padrão, com `JsonMapper` auto-configurado no lugar do `ObjectMapper` do Jackson 2; consultado em 21/09/2026 ao investigar o bug real #5 da Fase 5 (seção 105).
- `jwtk/jjwt` issue #1029 no GitHub — confirmação de que `jjwt-jackson` 0.13.0 ainda não suporta Jackson 3 (só Jackson 2), consultado em 21/09/2026 para entender por que `jackson-databind` (Jackson 2) continua legitimamente no classpath do projeto.
- Fórum oficial Hibernate ORM (discourse.hibernate.org), thread "PostgreSQL array of enums does not work with @JdbcType(PostgreSQLEnumJdbcType.class) but works with @Enumerated + @ColumnTransformer" — consultada em 22/09/2026 para o mapeamento de `Voluntario.funcoesHabilitadas` (Fase 6, seção 106); confirma um bug conhecido no Hibernate 7.x e a solução alternativa adotada.

Observação:

Limites de planos gratuitos e preços de fornecedores são externos ao código e podem mudar. Antes de uma contratação ou lançamento comercial, validar novamente os valores vigentes.

---

# 130. Conclusão

A arquitetura oficial do **Servire** no SaaS MVP passa a ser:

```text
Cloudflare Pages
        ↓
      Angular
        ↓
   Spring Boot
        ↓
  VPS + Docker
        ↓
+---------------------------+
| Supabase                  |
|                           |
| PostgreSQL compartilhado  |
| tenant_id                 |
|                           |
| Storage privado           |
+---------------------------+
```

Essa arquitetura reduz drasticamente:

- custo fixo;
- quantidade de serviços;
- complexidade de deploy;
- complexidade de pools;
- complexidade de migrations;
- esforço de provisionamento de clientes.

Ao mesmo tempo preserva uma fundação SaaS correta através de:

- TenantContext;
- Hibernate `@TenantId`;
- constraints tenant-aware;
- testes permanentes de isolamento;
- autenticação própria;
- auditoria;
- migrations versionadas;
- Storage privado;
- abstrações que permitem trocar os fornecedores posteriormente.

A evolução do **Servire** para AWS, banco dedicado por tenant ou infraestrutura distribuída deverá ocorrer apenas quando volume, receita, performance, SLA ou requisitos comerciais justificarem o custo adicional.

---

# 131. Adendo — respostas às 18 decisões de produto da seção 122 (21/09/2026)

O usuário respondeu às 18 perguntas da seção 122 diretamente em conversa, em blocos temáticos, e posteriormente confirmou os pontos que ainda estavam em aberto (item 1 e as duas recomendações dos itens 17 e 18). Esta seção registra cada resposta com o detalhamento necessário para implementação.

## 131.1 Item 1 — Multi-paróquia real no MVP (CONFIRMADO)

Pergunta: um usuário vai realmente gerenciar múltiplas paróquias no MVP, ou é só a arquitetura que permite isso no futuro?

Resposta inicial do usuário: *"Um usuário vai realmente gerenciar múltiplas paróquias no MVP"* — frase que repetia literalmente a primeira alternativa da pergunta, por isso havia sido registrada como interpretação a confirmar.

**Confirmação recebida em 21/09/2026:** *"sim, uso real multi-paróquia no MVP, mas ele vai ter que trocar de paróquia para acessar uma paróquia em específico e aí vai carregar os dados dela"* — o usuário ilustrou com um exemplo visual de referência: capturas de tela de um outro sistema (SIN — Gestão para Condomínios) mostrando exatamente o padrão de UX desejado:

1. No cabeçalho, ao lado do sino de notificações e do nome do usuário logado, existe um seletor mostrando a entidade ativa no momento (no exemplo, "Condomínio de Demonstração #56 - 30002").
2. Ao clicar nesse seletor, abre uma lista pesquisável ("Selecione o Condomínio Padrão de trabalho...") com busca por nome e a lista de todas as entidades (condomínios, no exemplo) disponíveis para aquele usuário.
3. Ao selecionar uma entidade diferente (no exemplo, "Cond Drácula"), o sistema exibe uma notificação de sucesso ("Condomínio alterado com sucesso!") e a tela atual (no exemplo, o Dashboard) recarrega automaticamente já filtrada pelos dados da nova entidade selecionada (novo nome/código no cabeçalho, novos números nos cards do dashboard).

**Decisão final (CONFIRMADA): SIM — um mesmo usuário vai realmente gerenciar mais de uma paróquia já no MVP**, trocando a paróquia ativa através de um seletor sempre visível no cabeçalho, análogo ao exemplo do sistema de condomínios acima (substituindo "condomínio" por "paróquia"). Ao trocar, a tela atual deve recarregar os dados já filtrados pela paróquia recém-selecionada. Isso não é mais uma leitura a confirmar — é a decisão de produto vigente. Ver notas atualizadas nas seções 29, 30 e 110, que passam a tratar a tela/seletor de troca de tenant como entregável real do MVP, não como validação técnica de arquitetura.

**Nota de design a observar na implementação:** o padrão de referência (busca + lista + troca com recarregamento) é um bom modelo de UX a seguir, mas os detalhes finos (onde exatamente o seletor fica no layout do Servire, se mostra código/ID da paróquia como no exemplo, etc.) ainda não foram desenhados especificamente para o Servire — usar o exemplo como inspiração de comportamento, não copiar o layout literalmente sem antes validar com o usuário/design do Servire.

## 131.2 Item 2 — Padre

Pergunta: o padre terá uma role própria no sistema?

Resposta: *"ele nem entra no sistema"*.

**Decisão: não.** O padre não terá login nem role no MVP. Removido da lista de roles futuras planejadas na seção 31 (item riscado). Se isso mudar no futuro, será uma nova decisão explícita, não uma suposição.

## 131.3 Itens 3, 4, 5, 6 e 15 — Monetização

- **Item 3 (limite de voluntários por plano):** sim, vai existir. Os números exatos (ex.: até quantos voluntários no Standard, até quantos no Pro) ainda não foram definidos — fica como pendência menor a resolver quando os planos forem desenhados em detalhe, não bloqueia a arquitetura (a estrutura `plano`/`preco_plano` da seção 63 já comporta qualquer limite).
- **Item 4 (planos):** nomes sugeridos pelo usuário — **"Standard"** e **"Pro"** —, cada um com opção de cobrança **mensal** e **anual com desconto**. Estrutura de preços exata (valores em R$) ainda não definida.
- **Item 5 (trial):** sim, **7 dias**.
- **Item 6 (dias de inadimplência até bloqueio):** **3 dias**.
- **Item 15 (gateway de pagamento):** o usuário ainda não decidiu um gateway. Para começar, a intenção é operar **somente via PIX, controlado manualmente por ele mesmo** (sem integração automatizada) — decisão explicitamente marcada por ele como "resolver depois". Ver notas de impacto nas seções 62, 64, 111 e 112: a estrutura de dados de billing (`plano`, `preco_plano`, `assinatura`, `cobranca`) deve ser criada normalmente, mas a liberação/registro de pagamento no MVP inicial é uma ação manual no backoffice, não um webhook automático. Quando o usuário decidir integrar um gateway de verdade, o candidato mais natural para PIX no Brasil (a avaliar com ele na hora, não uma decisão já tomada) costuma ser Asaas ou Mercado Pago, por terem PIX nativo e cobrança recorrente — mencionado aqui como contexto, não como decisão.

## 131.4 Item 7 — Bloqueio por inadimplência

Pergunta: `BLOQUEADO` permite modo somente leitura?

Resposta: *"totalmente travada"*.

**Decisão: não há modo somente leitura.** `BLOQUEADO` nega acesso por completo, no mesmo padrão de `CANCELADO` (seção 28 atualizada). Simplifica o Kill Switch, já que os dois status passam a ter o mesmo comportamento de negação — a diferença entre eles é só semântica/comercial (um é reversível com o pagamento, o outro não).

## 131.5 Itens 8 a 12 — Funcionalidades de produto (parte 1)

- **Item 8 (WhatsApp):** não, por enquanto. O campo `autoriza_whatsapp` que já existe no cadastro (seção 37 do plano mestre, seção 8 do documento técnico) continua sendo só um dado coletado, sem automação por trás.
- **Item 9 (app mobile):** não. Só o site responsivo (Angular atual) por enquanto — sem app nativo nem PWA instalável mencionado.
- **Item 10 (confirmação de presença):** não, por enquanto.
- **Item 11 (controle de faltas):** **sim.** Implica uma nova estrutura de dados tenant-aware para registrar falta por participação em escala (ex.: novo status em `escala_vaga` ou tabela própria `falta`) — a incluir no desenho da FASE 9 (seção 109).
- **Item 12 (disponibilidade do voluntário):** **sim.** Implica uma nova entidade tenant-aware (ex. `disponibilidade_voluntario`, por dia/horário) usada como filtro adicional no picker de candidatos (seção 49) — também a incluir na FASE 9.

## 131.6 Itens 13 e 14 — Funcionalidades de produto (parte 2)

- **Item 13 (portal do responsável):** não, por enquanto. O responsável continua sendo só um registro de contato vinculado ao voluntário (seção 38), sem login próprio.
- **Item 14 (escala pública):** **sim, mas protegida por uma chave secreta que o coordenador cadastra** — não é uma URL totalmente aberta ao público em geral, é mais parecida com um "link com senha" do que com uma página pública sem controle nenhum. Isso é tecnicamente parecido com a ideia de token de expiração que o próprio usuário já havia pedido para o link de inscrição pública (seção 20.9 do documento técnico) — vale desenhar os dois mecanismos (chave da escala pública e token/expiração da inscrição pública) de forma consistente quando chegar a hora de implementar, em vez de dois sistemas de token paralelos e diferentes.

## 131.7 Item 17 — Política de retenção LGPD (CONFIRMADO)

O usuário pediu recomendação, deixando claro que não tinha conhecimento sobre o tema, e em 21/09/2026 confirmou explicitamente: *"pode ser a sua sugestão"*.

**Ressalva mantida (importante): não sou advogado, isto não é aconselhamento jurídico formal** — é uma sugestão técnica de ponto de partida, baseada em práticas comuns de retenção de dados e no que a LGPD exige em linhas gerais para dados de crianças e adolescentes (art. 14). Dado que o sistema trata dados de menores, ainda recomendo validar esta política com um advogado ou DPO antes de operar com dados reais de mais de uma paróquia — a confirmação do usuário adota isso como política vigente de produto/engenharia, não substitui uma validação jurídica formal.

**Decisão final (política vigente):**

- **Inscrição REJEITADA:** não há vínculo nem justificativa para reter os dados. Anonimizar ou excluir nome, endereço, telefone, e-mail e foto em até **30 dias** após a rejeição, mantendo no máximo um registro estatístico anônimo (ex.: "1 inscrição rejeitada em tal data, motivo tal") se for útil para relatórios.
- **Voluntário aprovado que depois fica inativo (`ativo=false`):** manter os dados completos por um período após a inativação para fins de histórico de escalas (relatórios, "quem serviu quando") — **até 24 meses**. Depois disso, anonimizar os campos diretamente identificáveis (nome completo, endereço, telefone, e-mail, foto), mantendo apenas um vínculo genérico com o histórico de escalas (ex.: "Voluntário #123 serviu em tal escala"), se esse histórico precisar ser preservado.
- **Fotos:** por serem mais sensíveis e diretamente identificáveis (ainda mais tratando-se de menores), prazo de exclusão mais curto que os demais dados — **6 a 12 meses** após a inativação, mesmo que outros dados textuais sejam mantidos um pouco mais.
- **Consentimento:** hoje o formulário de inscrição (seção 12.1 do documento técnico) só tem um checkbox de autorização para grupo de WhatsApp — não há nenhum checkbox de consentimento para tratamento de dados pessoais do menor. Como quem preenche o formulário já é o responsável legal (não o menor), esse é o ponto natural para incluir um consentimento explícito e destacado (ex.: "Autorizo o tratamento dos dados deste menor para fins de organização das escalas da paróquia, conforme [link para política de privacidade]"), o que ajuda a atender ao requisito do art. 14 da LGPD de consentimento específico e em destaque de um dos pais/responsável legal.
- **Direito de exclusão antecipada:** prever um canal (mesmo que manual, via contato com a coordenação, no MVP) para o responsável pedir a exclusão dos dados do menor antes do prazo acima, já que esse é um direito do titular garantido pela LGPD independente da política de retenção padrão.

Os prazos específicos (30 dias / 24 meses / 6-12 meses) são a política adotada como ponto de partida; ainda vale revisá-los com apoio jurídico formal antes de tratá-los como definitivos para uso comercial com dados reais de terceiros.

## 131.8 Item 16 — Domínio oficial

Resposta do usuário: ainda não registrado, mas será **`servirea.com.br`**.

**Decisão: `servirea.com.br` é o domínio oficial planejado.** Os exemplos anteriores neste documento (`servire.app.br`, `app.servire.app.br`, `api.servire.app.br`) eram placeholders de antes desta decisão — ver notas nas seções 12, 91 e 113 apontando para esta atualização. Quando o domínio for efetivamente registrado, atualizar esses exemplos para `servirea.com.br` e seus subdomínios (ex. `app.servirea.com.br`, `api.servirea.com.br`).

## 131.9 Item 18 — Destino dos backups externos (CONFIRMADO)

O usuário pediu recomendação, sem conhecimento prévio sobre o tema, e em 21/09/2026 confirmou: *"vamos usar o Cloudflare R2 então"*.

**Decisão final: Cloudflare R2** é o destino oficial dos backups externos — compatível com S3 (mesma interface que a seção 53/84 já prevê para uma eventual troca do Supabase Storage), tem camada gratuita generosa e, diferente da AWS S3, **não cobra taxa de saída (egress)**, o que importa se um dia for necessário restaurar um backup grande.

- **Mecanismo:** manter o fluxo já descrito na seção 3.4 (`cron no VPS → pg_dump → compactação → criptografia → retenção controlada`), com o passo final sendo o upload do arquivo compactado e criptografado para o bucket R2 em vez de deixá-lo só no VPS.
- **Retenção sugerida:** rotação de **7 backups diários + 4 semanais + 3 mensais**, apagando automaticamente os mais antigos fora dessa janela — evita crescimento infinito de custo de armazenamento e ainda cobre a maioria dos cenários de recuperação (erro recente, erro de algumas semanas atrás, ou necessidade de comparar com um estado de meses atrás).
- **Criptografia:** como os dados incluem informações de menores (nome, endereço, telefone, fotos), o arquivo de backup deve ser criptografado antes do upload (ex. `gpg` com chave simétrica forte, ou client-side encryption do próprio provedor de storage), não só protegido por controle de acesso do bucket.
- **Teste de restauração:** reforçando o que a seção 3.4 já exige — um backup só é considerado válido depois de já ter sido restaurado com sucesso pelo menos uma vez em ambiente de teste; isso deve incluir também o backup que for parar no R2, não só o dump local.

A política de rotação (7 diárias + 4 semanais + 3 mensais) segue como sugestão técnica a confirmar quando o VPS for efetivamente contratado (seção 1.2) e o pipeline de backup for implementado; o destino (Cloudflare R2) já é decisão fechada.
