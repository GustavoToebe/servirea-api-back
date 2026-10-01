# Servirea API

Backend Java do **Servirea** (SaaS multi-tenant de gestão paroquial).
Este repo é o `servire-api-back`; o Angular é o irmão `servire-api-front`
(outro git). Idioma do código e dos commits: **português**.

- Instruções para quem mexe no código: [`AGENTS.md`](AGENTS.md)
- Diário das fases, bugs e builds: [`HISTORICO.md`](HISTORICO.md)
- Plano de produto: `plano_mestre_servire_v2_mvp_baixo_custo.md`

**Última confirmação (25/09/2026):** `mvn verify` com `BUILD SUCCESS`,
193 testes, 0 falhas (V037 incluída, com `FluxoHttpIntegrationTest` e
`IntegracaoHttpIntegrationTest` exercitando a API via HTTP).

## Estado atual

| Módulo | Situação | Contrato |
|---|---|---|
| Auth (`/auth/**`) | Pronto | Login, refresh em cookie, reset de senha e convite (Resend). Link cai em `/reset-password?token=` do front |
| Tenant (`/tenant`) | Pronto | `GET`/`PUT`; e-mails e telefones em lista 1:N |
| Pessoas (`/pessoas/**`) | Pronto (V030–V032) | Cadastro. **Substitui** `POST`/`PUT /voluntarios` |
| Formatos (`web/Formatos`) | Pronto (26/09/2026) | CPF, CNPJ (inclusive alfanumérico), RG, CEP, UF, telefone e sexo (Masculino/Feminino/Outro) validados no service e gravados formatados (`529.982.247-25`, `(45) 99999-8888`, `85800-000`); inválido = 400 com a mensagem. E-mail exige domínio com ponto. Vale em pessoa, inscrição, paróquia, usuários e `/me` |
| Voluntários (`/voluntarios/**`) | Perfil só | Lista, ativo, foto, commitments — sem criar/editar identidade |
| Inscrições | Pronto | Público + fila; responsável **opcional**; aprovar materializa `Pessoa` |
| Escalas (`/escalas/**`) | Pronto | Eventos, vagas, presença, picker, alocação. Front já usa a API |
| Storage | Pronto | Bucket privado `voluntarios-fotos` |
| Auditoria | Pronto | `GET /audit-log` (ADMIN da paróquia) |
| Perfis e usuários | Etapa 1 | `/perfis`, `/usuarios` (convite de 7 dias, sem senha no formulário), `/me` (inclui `permissoes` da paróquia atual). Cada endpoint pede a ação do catálogo (`PERM_ESCALA_EXCLUIR`…); ninguém concede mais do que tem |
| Integração Central | Etapa 2 | `/integracao/v1` com HMAC (`PERM_INTEGRACAO`). Sem chave configurada, a rota recusa; sem `direitos_locais`, a paróquia segue no status do tenant. Login e filtro usam a mesma regra (72h). Publica o catálogo de recursos (`GET /integracao/v1/recursos`) e manda os erros 5xx à tela "Logs" da Central (contrato 5.5 e 6.2) |
| Escala: replicar e mensal | V044–V045 | `referencia` no evento (linha da escala replicada, fora de finalizar/presença/compromissos); `GET`/`PUT /escalas/indisponibilidades?ano=&mes=` (substitui o mês); `GET /escalas/{id}/apoio` (situação, datas indisponíveis e irmãos por voluntário ativo) |
| Layouts de envio (`/layouts/**`) | V042 | CRUD, `GET /layouts/tags?tipoLayout=` e `POST /layouts/pre-visualizar`; tags trocadas pelo `Renderizador` |
| Comunicados (`/comunicados/**`) | V043 | `POST /comunicados/destinatarios`, `POST /comunicados/pre-visualizar`, `POST /comunicados` (multipart `dados` + `anexos`, vai para a fila), `GET /comunicados`, `GET /comunicados/{id}`, `POST /comunicados/{id}/reenviar-falhas`. E-mail pelo Resend, WhatsApp pelo Evolution Go |
| WhatsApp da paróquia (`/tenant/whatsapp`) | V043 | `GET` (sem o token), `PUT` (token vazio mantém), `POST /tenant/whatsapp/testar` |
| Número curto | V038 | `sequencial` por paróquia em pessoa, escala, inscrição, perfil e usuário, para ditar e copiar |
| Front Angular | Migrado | Login JWT, `/pessoas`, inscrições, escalas, perfis, usuários e meu perfil. O painel `/admin` saiu |

## Cadastro pessoa-primeiro — contrato para o Angular

Quebra deliberada. Não há shim dos endpoints antigos.

| Era | É |
|---|---|
| `POST`/`PUT /voluntarios` | `POST`/`PUT /pessoas` |
| `GET /voluntarios` | Continua (só o perfil de escala) |
| Tabela `responsaveis` | `pessoa` + `pessoa_relacao` |
| Um e-mail/telefone solto | Listas `{ tipo, valor, principal }` |
| Papel exclusivo | `papeis: ["VOLUNTARIO"]` e/ou `["RESPONSAVEL"]` |
| Ficha e depois `POST /voluntarios/{id}/foto` | Com foto: `POST`/`PUT /pessoas` em `multipart/form-data` (`dados` = JSON, `foto`). Foto que falha não grava a ficha (26/09/2026) |

```json
{
  "papeis": ["VOLUNTARIO", "RESPONSAVEL"],
  "nomeCompleto": "Maria Silva",
  "emails": [{ "tipo": "E-mail pessoal", "email": "maria@paroquia.org", "principal": true }],
  "telefones": [{ "tipo": "celular", "numero": "11999990000", "principal": true }],
  "responsaveis": [
    { "pessoaId": "…uuid…", "parentesco": "Mãe", "parentescoInverso": "Filha", "principal": true },
    { "novaPessoa": { "nomeCompleto": "Avó Nova", "email": null, "telefone": null },
      "parentesco": "Avó", "parentescoInverso": "Neta", "principal": false }
  ],
  "dependentes": [
    { "pessoaId": "…uuid…", "parentesco": "Filho", "parentescoInverso": "Mãe", "principal": true }
  ],
  "voluntario": { "tipo": "COROINHA", "ativo": true, "autorizaWhatsapp": false, "funcoesHabilitadas": [] }
}
```

A resposta devolve documentos e contatos já formatados (o telefone acima volta `(11) 99999-0000`).

Regras de negócio (V031):
- Uma pessoa pode ser **os dois** (ministro que também é pai/mãe).
- Dá para **acrescentar** papel; não dá para remover.
- Relações em **duas listas**, uma por lado: `responsaveis` (quem
  responde por esta pessoa — exige `VOLUNTARIO`) e `dependentes` (por quem
  ela responde — exige `RESPONSAVEL`). A resposta vem no mesmo formato, então
  o front reenvia o que recebeu. Lista vazia/nula apaga aquele lado.
- Em cada item, `parentesco` = o que a **outra** pessoa é; `parentescoInverso`
  = o que **esta** pessoa é para ela. Mesmo sentido nas duas listas.
- `novaPessoa` (só em `responsaveis`) cria o responsável na mesma transação.
- Responsáveis são **opcionais**. Adulto/ministro entra sem nenhum. Se vier
  mais de um, exatamente um `principal: true`.
- `tipo` do voluntário: `COROINHA`, `ACOLITO`, `AMBOS` ou `MESC` (ministro
  da comunhão). `mandatoInicio` e `mandatoFim` são opcionais; se os dois
  vierem, o vencimento não pode ser anterior à investidura.
- Diocese é só agrupamento informativo (V037, sem cota). Vai no
  `PUT /tenant` como `diocese` (nome; vazio = sem diocese) e volta no
  `GET /tenant`. Nome igual sem diferenciar maiúsculas reaproveita a mesma
  diocese; `GET /dioceses` (`PERM_PAROQUIA`) lista as já usadas para sugerir.
- Dependente marcado `principal` quando o voluntário já tem outro principal → 409.
- `GET /pessoas?papel=VOLUNTARIO` inclui quem também é responsável.

Inscrição pública: `responsaveis` também é opcional. Se mandar a lista,
exatamente um principal. Aprovar reusa, pelo e-mail principal, quem já é
`RESPONSAVEL`; só promove um voluntário a responsável se o **nome** também
bater (criança inscrita com o e-mail da mãe não vira responsável de si
mesma nem do irmão). Sem candidato, cria a pessoa.

## CSRF

`csrf.spa()` com uma exceção: requisição com `Authorization: Bearer` fora de
`/auth/**` e `/admin/auth/**` não precisa de `X-XSRF-TOKEN` (o token mora em
`sessionStorage`, não é credencial ambiente). `refresh`/`logout` usam o cookie
HttpOnly e continuam exigindo o header. Em produção, front e API em hosts
diferentes: `CSRF_COOKIE_DOMAIN=servirea.com.br` para o front conseguir ler o
cookie `XSRF-TOKEN`.

### Produção (banco) — estado em 24/09/2026

O Supabase de produção **nunca tinha passado pelo Flyway**: estava no
estado da V015 (criado pelo `supabase/schema.sql` do front), sem
`flyway_schema_history`. Com só 6 pessoas de teste, a decisão foi
**apagar e recriar**: um script único (V001–V032 na ordem + histórico do
Flyway com os checksums reais + RLS ligado em todas as tabelas do
`public`) rodou pelo `psql`. Os dados antigos foram descartados; o
primeiro ADMIN foi criado à mão (ver HISTORICO, "Produção recriada").

Daqui em diante é o fluxo normal: migration nova (`V033+`) é aplicada
pelo próprio Flyway quando a API sobe.

- SQL manual em produção: rodar pelo `psql`, não pelo SQL Editor. O
  botão "Run and enable RLS" do editor injeta `ALTER TABLE` no meio de
  blocos `DO $$ ... $$` e corrompe o script.
- Senha com `@` quebra a URL `postgresql://`: passar `-h/-U` e
  `PGPASSWORD` separados.
- Criar usuário à mão: `'{bcrypt}' || extensions.crypt('senha', extensions.gen_salt('bf', 10))`
  (pgcrypto do Supabase fica no schema `extensions`).

## Reverse proxy e rate limit

O limitador da inscrição pública é **em memória**, 5 tentativas / hora /
IP, uma instância. Redis fica para quando houver mais de uma réplica.

A API **não lê** `X-Forwarded-For`. Usa `getRemoteAddr()`. Em produção
o Tomcat (`forward-headers-strategy: native`) só reescreve o IP se o
hop imediato estiver em `server.tomcat.remoteip.internal-proxies`
(loopback e redes privadas).

O proxy tem que **sobrescrever** o header, não concatenar o valor que
o cliente mandou:

```nginx
# Nginx — NÃO use $proxy_add_x_forwarded_for
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header Host $host;
```

```caddy
# Caddy (reverse_proxy já define o header pelo hop; não repasse o do cliente)
reverse_proxy 127.0.0.1:8080 {
    header_up X-Forwarded-For {remote_host}
}
```

O Caddy comprime a resposta da API (`encode zstd gzip` no site
`api.servirea.com.br` e no `/api` do app). O Spring **não** liga
`server.compression`: comprimir de novo no Tomcat só gastaria CPU
(e o Tomcat só faz gzip, sem zstd). O front em produção chama
`https://app.servirea.com.br/api`, mesma origem, sem preflight.
O host `api.servirea.com.br` continua para quem ainda não atualizou.

## Como rodar

Docker Desktop aberto para os testes (Testcontainers). Segredos de
dev em `application-dev-local.yml` (gitignorado).

```powershell
mvn clean verify
mvn test "-Dtest=PessoaServiceIntegrationTest"
mvn spring-boot:run -DskipTests "-Dspring-boot.run.profiles=dev"
```

`spring-boot:run` contra Postgres limpo precisa do stub
`src/test/resources/testcontainers/supabase-stubs.sql` antes do
Flyway. Detalhe em `HISTORICO.md`, seção "Como rodar localmente".

**Tudo junto no PC** (Servirea + Central + os dois fronts, Postgres no Docker, comando para
dar pull nos quatro repositórios e problemas já vistos): README do
[`central-api-back`](https://github.com/GustavoToebe/central-api-back), seção
"Ponta a ponta local (Windows, tudo no PC)". O atalho do banco é
`central-api-back/scripts/subir-local.ps1`. No profile `dev`, com o JDBC em
`localhost`, esta API cria a paróquia `paroquia-teste` (login
`paroquia@teste.local` / `12345678`) se ela ainda não existir. Não aponta para o Supabase.

## Onde está o código

```
Documents/servire/
  servire-api-back/     ← este repo
  servire-api-front/    ← Angular (outro git)
```

GitHub: [servirea-api-back](https://github.com/GustavoToebe/servirea-api-back),
[servirea-api-front](https://github.com/GustavoToebe/servirea-api-front).

Stack: Java 21 · Spring Boot 4.1.1 · Hibernate 7.4 · PostgreSQL 17 (Supabase) ·
Flyway V001–V037 · JJWT 0.13 · Testcontainers 2.x.

## Próximos passos

Em produção desde 27/09/2026: `https://app.servirea.com.br` e `https://api.servirea.com.br` (VPS em Montreal,
Supabase ca-central-1 criado do zero, V001–V039). Deploy e variáveis: `deploy/` do
[`central-api-back`](https://github.com/GustavoToebe/central-api-back). Supabase com a Data API e o cadastro do
Auth desligados; a V039 fecha o `public` de qualquer forma.

1. Criar a paróquia real pela Central (contratação → provisionamento → convite) e recadastrar as pessoas.
2. Paróquia `placeholder` (V020) e `publicTenantSlug` do front: trocar pelo slug da paróquia real.
3. Ajustes pedidos no uso (lista de assinatura e demais telas) e bugs.
4. Escala do mês por WhatsApp e e-mail para os pais (via Central; só quem autorizou).
5. Storage das fotos (`SUPABASE_URL`/`SUPABASE_SERVICE_ROLE_KEY` vazias em produção: só o envio de foto falha).
6. Feito na V051: `plano`, `preco_plano`, `assinatura`, `cobranca`, `backoffice_log` e
   `usuario.operador_saas`. O que permanece está em `SCHEMA.md`.

## Financeiro paroquial

Módulo simples em `/financeiro`: contas/bancos, categorias, entradas, saídas, baixa manual, estorno e saldos. Liberação por perfil; [uso e regras](docs/financeiro.md).
