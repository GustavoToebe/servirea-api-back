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

Cada módulo tem um documento com contrato, limites e homologação em `docs/`. Aqui fica só a regra que não pode ser quebrada; ao mexer no módulo, leia o documento.

| Módulo | Documento | Regra que não pode ser quebrada |
|---|---|---|
| Integração com a Central | [integracao-limites](docs/integracao-limites.md) | HMAC antes de ler o corpo, 1 MiB, nonce; `/integracao` nunca é `permitAll` |
| Cópias com a Central | `central-api-back/scripts/verificar-copias.py` | `IntegracaoNonceService`, `LimiteLogin`, `Contador*` e `MonitoramentoFilter` são idênticos nos dois back; mudou um, mude o outro e rode o script na pasta da Central |
| Financeiro paroquial | [financeiro](docs/financeiro.md) | Permissões próprias, baixa/estorno com versão, isolamento por paróquia |
| Cuidados e credenciais | [seguranca-cuidados-credenciais](docs/seguranca-cuidados-credenciais.md) | DTO de resposta autorizada, AES-GCM com rotação, nunca devolver token |
| Fila de comunicados | [fila-comunicados](docs/fila-comunicados.md) | HTTP fora da transação; ordem de lock janela → comunicado → destinatário |
| Cotas e plano | [cotas-plano](docs/cotas-plano.md), [armazenamento-cotas](docs/armazenamento-cotas.md), [cotas-envios-importacao](docs/cotas-envios-importacao.md), [funcionalidades-plano](docs/funcionalidades-plano.md) | Reservar a paróquia antes do domínio e validar após flush; NULL em tamanho de arquivo é inventário pendente |
| Importação de pessoas | [importacao-pessoas](docs/importacao-pessoas.md) | Atômica, sem mesclar fichas, hash inclui arquivo/aba/mapeamento |
| Mural e tarefas | [mural-tarefas](docs/mural-tarefas.md), [mural-publico-leituras](docs/mural-publico-leituras.md) | Público não concede permissão; leitura vale para a versão exata |
| Portal, calendário, pastorais | [portal-calendario-pastorais](docs/portal-calendario-pastorais.md) | `UsuarioTenant.pessoaId` explícito, nunca inferido; token de calendário só como hash e nunca em log |
| Resposta, candidatura e troca | [respostas-escala](docs/respostas-escala.md), [candidaturas-vagas](docs/candidaturas-vagas.md), [trocas-escala](docs/trocas-escala.md) | Só a pessoa própria; locks paróquia → escala → vaga; reabrir invalida pendentes |
| Tarefas, relatórios, disponibilidade | [tarefas-relatorios-disponibilidade](docs/tarefas-relatorios-disponibilidade.md) | CSV até 5.000 linhas com AUDITORIA + RELATORIO_EXPORTAR; PUT mensal exige `versao` |
| Primeiros passos | [onboarding](docs/onboarding.md) | GET sem escrita; requisitos conferidos pelo servidor; nada automático |
| Aniversários, site público | [aniversarios](docs/aniversarios.md), [site-publico](docs/site-publico.md) | Opt-in por pessoa e canal; agendador desligado; página pública só por snapshot explícito |
| Liturgia, indicadores, estoque, seletores | [liturgia](docs/liturgia.md), [indicadores-participacao](docs/indicadores-participacao.md), [estoque-patrimonio](docs/estoque-patrimonio.md), [seletores-escala](docs/seletores-escala.md) | Sem geração de conteúdo; estoque só muda por movimento com chave/versão; `/voluntarios/painel` conta no banco |
| Distribuição por regras | [distribuicao-escala](docs/distribuicao-escala.md) | Prévia sem efeito; aplicar trava a escala, confere versão e recalcula (`VAGA_DISTRIBUIR` + `VAGA_ALOCAR`) |
| Notificações e entregas | [notificacoes-entregas](docs/notificacoes-entregas.md) | Só enfileira por `EnvioAvulso`; uma entrega por origem+versão+canal; WhatsApp só com autorização; gatilhos nascem desligados |
| Privacidade | [privacidade](docs/privacidade.md) | Exportação sem corpo de mensagem nem destino; cuidados só com `PESSOA_CUIDADOS_LER`; retenção manual e irreversível |
| Check-in | [checkin-encontro](docs/checkin-encontro.md) | Token só como hash, 404 genérico, um registro por vaga, `FALTOU` não é sobrescrito |
| Arquivos | [arquivos-ciclo-vida](docs/arquivos-ciclo-vida.md) | UPLOAD/REMOCAO duráveis; nunca apagar caminho ainda referenciado; pendência em transação própria só fora de transação que trava a paróquia |
| Métricas | [monitoramento](docs/monitoramento.md) | Credencial exclusiva, sem dado de negócio nem rótulo livre; não ativar operação externa |
| MFA | [mfa-usuarios](docs/mfa-usuarios.md) | TOTP e recuperação globais; nunca contornar por redefinição por e-mail |
| Login | [login-limites](docs/login-limites.md) | Contador no banco (`login_tentativa`, V082), por IP e por conta; ver o compromisso no doc |
| Acessos pessoais | [acessos-responsaveis-coordenacao](docs/acessos-responsaveis-coordenacao.md) | Dependente só com autorização explícita; parentesco não concede acesso; coordenação própria revalida a cada operação |
| Papéis do banco | [papeis-banco](docs/papeis-banco.md) | `MIGRATION_DB_*` separa o Flyway; app só dados; ensaiar em staging |
| Contrato da API | [contrato-api](docs/contrato-api.md) | Mudou rota, permissão ou DTO: regenerar `docs/contrato-api.json`; rota nova exige `@PreAuthorize` |

## Documentação

Não copiar blocos históricos para instruções vigentes. Ao retomar, registrar o que foi alterado, comandos realmente executados, resultado e pendência concreta; não marcar teste planejado como aprovado nem implementação local como publicada. Validar links e estado com `python scripts/verificar-docs.py`; PRs e mudanças documentais rodam essa checagem na CI. Precedência e histórico: [fontes e retomada](docs/desenvolvimento/fontes-e-retomada.md).

- [Biblioteca comum](docs/biblioteca-comum.md): HMAC/TOTP vêm de versão Maven fixa; sem cópias locais.
