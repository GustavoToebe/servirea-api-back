# AGENTS.md — servirea-api-back

Backend Java do Servirea, gestão paroquial multi-tenant. Frontend: repositório separado `servirea-api-front`. Código, mensagens e commits em português. `CLAUDE.md` importa este arquivo; não duplicar instruções nele.

## Primeiro acesso e fontes

- [Estado verificável](docs/estado-projeto.json): componente, comandos e última migration local. Não é um manifesto de produção.
- [Índice](docs/README.md) e [mapa de módulos](docs/desenvolvimento/mapa-backend.md): onde alterar cada função.
- [README.md](README.md): execução local. [SCHEMA.md](SCHEMA.md) e [schema.sql](schema.sql): banco vigente no código.
- [Armadilhas Java](docs/desenvolvimento/armadilhas-java.md): leitura obrigatória antes de persistência, DTO, segurança, storage ou integração.
- [Testes](docs/desenvolvimento/testes.md): integração, tenant e execução no Windows.
- [HISTORICO.md](HISTORICO.md) e plano mestre são contexto histórico/de produto. Código, migrations e contratos atuais prevalecem sobre descrições históricas.

## Trabalho e publicação

Atualizar regras e documentos afetados junto com código. Nesta tarefa, usar a branch solicitada `melhoria/ecossistema-sem-ia` nos quatro repositórios. Não tratar autorização de uma sessão passada como autorização de deploy atual.

Deploy depende de pedido do usuário; nova migration destrutiva exige conferir backup. Fluxo atual no repositório `central-api-back/deploy/README.md`. CI testa PRs; publicação aceita somente main. Pastas esperadas pelo script atual: `/opt/ecossistema/servirea-api-back`, `servirea-api-front`, `central-api-back`, `central-api-front`. Estado real da VPS precisa ser verificado; não assumir versão implantada.

## Stack e comandos

Java 21, Spring Boot 4.1.1, Hibernate 7, PostgreSQL 17, Flyway, Maven, Jackson 3 (`tools.jackson.*`), MVC com virtual threads. Sem WebFlux.

```powershell
mvn clean verify
mvn spring-boot:run -DskipTests "-Dspring-boot.run.profiles=dev"
python scripts/verificar-docs.py
```

Docker precisa estar ativo nos testes. Dev local usa `application-dev-local.yml` gitignorado. PostgreSQL local limpo exige stubs Supabase; instruções no README. Segredos nunca no Git ou logs.

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
  `TenantContext` por conta própria (`InscricaoService.criarPublica`, `CalendarioService.feed` e integração HMAC) precisa abrir a transação
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
  WhatsApp dos comunicados: `WHATSAPP_PROVIDER` (`log` padrão, `evolution`) e `EVOLUTION_URL`; o token é de cada paróquia (`/tenant/whatsapp`).

## Schema e verificação

- Toda tabela nova em public: RLS habilitada sem policy; revogar anon/authenticated. Java permanece separado da Data API.
- Não editar migration já aplicada. Atualizar total e descrição em FlywayMigrationIntegrationTest, gerar schema em banco descartável e atualizar estado-projeto.json.
- Teste cria tenant próprio, define contexto antes da transação e limpa depois. Não usar @Transactional na classe de teste de domínio.
- Alteração em DTO/rota exige teste HTTP pertinente; controlar leitura de coleções lazy com open-in-view false.

## Regras por funcionalidade

- [Integração](docs/integracao-limites.md): HMAC antes de ler payload, 1 MiB, nonce e autorização; /integracao não é permitAll.
- [Financeiro](docs/financeiro.md): contas, categorias, baixa/estorno, permissões próprias e isolamento.
- [Cuidados e credenciais](docs/seguranca-cuidados-credenciais.md): DTO deAutorizada nos endpoints, permissões específicas, AES-GCM e rotação; nunca devolver token.
- [Fila](docs/fila-comunicados.md): HTTP fora da transação, posse/expiração e lock janela → comunicado → destinatário. Resend recebe chave estável; limites de idempotência documentados.

## Documentação

Não copiar blocos históricos para instruções vigentes. Ao retomar, registrar o que foi alterado, comandos realmente executados, resultado e pendência concreta; não marcar teste planejado como aprovado nem implementação local como publicada. Validar links e estado com `python scripts/verificar-docs.py`; PRs e mudanças documentais rodam essa checagem na CI.

- Cotas locais: docs/cotas-plano.md. Reservar paróquia antes do domínio e validar após flush em toda criação/promoção de pessoa, aprovação e ativação de vínculo. Armazenamento de arquivos vinculados: docs/armazenamento-cotas.md (V057); NULL é inventário pendente, HEAD fora da transação. Envios mensais e CSV de pessoas: docs/cotas-envios-importacao.md (V058–V059). Reserva da fila trava paróquia antes de janela/comunicado/destinatário; competência nunca é apagada em reenvio. Importação atômica, chave por tenant, sem guardar CSV. XLSX/documentos gerais pendentes.

- Nova rodada de produto: docs/funcionalidades-plano.md e docs/mural-tarefas.md. Recursos explícitos, leitura preservada, mutações protegidas; tabelas V060, histórico versionado, sem HTML/notificações.

- Portal/calendário/pastorais: docs/portal-calendario-pastorais.md (V061). UsuarioTenant.pessoaId explícito, nunca inferido; calendario_assinatura global resolve tenant por hash secreto antes de TransactionTemplate. Portal consulta/responde somente pela pessoa própria; feed revalida vínculo/plano/perfil. Não registrar token/URL. Pastorais @TenantId, sem escopo de permissão por equipe.

- V062: docs/respostas-escala.md. PORTAL_RESPONDER só para pessoa própria; prazo no início da celebração, servidor Brasília→UTC. Recusa não desaloca/presença. Reabrir/trocar pessoa invalida decisão. @Version vaga; lock paróquia→escala para resposta, escala para edição/alocação. Histórico mantém UUIDs após exclusão. VAGA_RESPOSTA_LER para consulta da coordenação.
