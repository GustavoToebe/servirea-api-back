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


## Windows

Dentro do aplicativo Windows, usar quando o JDK falhar ao criar o socket temporário:

```powershell
mvn -o -B -q verify "-DargLine=-Djdk.net.unixdomain.tmpdir=C:\Users\ICONDESKTOP_02\Documents\Codex\java-tmp"
```

Criar previamente o diretório. Este argumento é local; não configura produção.

O profile test limita cada Hikari a quatro conexões/minimum-idle um; contextos com mocks diferentes compartilham o PostgreSQL singleton. Não elevar os pools para contornar pressão de contextos da suíte. Testes das cotas exercitam rodada de uma paróquia para não depender das filas deixadas por outras fixtures.
