# Servire API

Backend Java do **Servire** (SaaS multi-tenant de gestão paroquial).
Este repo é o `servire-api-back`; o Angular é o irmão `servire-api-front`
(outro git). Idioma do código e dos commits: **português**.

- Instruções para quem mexe no código: [`AGENTS.md`](AGENTS.md)
- Diário das fases, bugs e builds: [`HISTORICO.md`](HISTORICO.md)
- Plano de produto: `plano_mestre_servire_v2_mvp_baixo_custo.md`

**Última confirmação nesta máquina (24/09/2026):** `mvn test` com
`BUILD SUCCESS`, 190 testes, 0 falhas (V031 incluída).

## Estado atual

| Módulo | Situação | Contrato |
|---|---|---|
| Auth (`/auth/**`) | Pronto | Login, refresh em cookie, reset de senha (Resend) |
| Tenant (`/tenant`) | Pronto | `GET`/`PUT`; e-mails e telefones em lista 1:N |
| Pessoas (`/pessoas/**`) | Pronto (V030+V031) | Cadastro. **Substitui** `POST`/`PUT /voluntarios` |
| Voluntários (`/voluntarios/**`) | Perfil só | Lista, ativo, foto, commitments — sem criar/editar identidade |
| Inscrições | Pronto | Público + fila; responsável **opcional**; aprovar materializa `Pessoa` |
| Escalas | Pronto | Eventos, vagas, presença, picker, alocação |
| Storage | Pronto | Bucket privado `voluntarios-fotos` |
| Auditoria | Pronto | `GET /audit-log` (ADMIN da paróquia) |
| Backoffice (`/admin/**`) | Pronto | Operador SaaS, paróquias, suporte |
| Billing | Manual | Planos, assinatura, cobrança, PIX manual. Sem gateway / bloqueio automático |
| Front Angular | **Quebrado até migrar** | Ainda fala com `POST`/`PUT /voluntarios` e tabela `responsaveis` |

## Cadastro pessoa-primeiro — contrato para o Angular

Quebra deliberada. Não há shim dos endpoints antigos.

| Era | É |
|---|---|
| `POST`/`PUT /voluntarios` | `POST`/`PUT /pessoas` |
| `GET /voluntarios` | Continua (só o perfil de escala) |
| Tabela `responsaveis` | `pessoa` + `pessoa_relacao` |
| Um e-mail/telefone solto | Listas `{ tipo, valor, principal }` |
| Papel exclusivo | `papeis: ["VOLUNTARIO"]` e/ou `["RESPONSAVEL"]` |

```json
{
  "papeis": ["VOLUNTARIO", "RESPONSAVEL"],
  "nomeCompleto": "Maria Silva",
  "emails": [{ "tipo": "E-mail pessoal", "email": "maria@paroquia.org", "principal": true }],
  "telefones": [{ "tipo": "celular", "numero": "11999990000", "principal": true }],
  "relacoes": [],
  "voluntario": { "tipo": "COROINHA", "ativo": true, "autorizaWhatsapp": false, "funcoesHabilitadas": [] }
}
```

Regras de negócio (V031):
- Uma pessoa pode ser **os dois** (ministro que também é pai/mãe).
- Dá para **acrescentar** papel; não dá para remover.
- `relacoes` é **opcional**. Adulto/ministro entra sem responsável.
- Se vier mais de uma relação, exatamente uma `principal: true`.
- `GET /pessoas?papel=VOLUNTARIO` inclui quem também é responsável.

Inscrição pública: `responsaveis` também é opcional. Se mandar a lista,
exatamente um principal. Aprovar reusa a pessoa pelo e-mail principal
(e acrescenta `RESPONSAVEL` se ela só era voluntária).

### Antes de aplicar V030/V031 em produção

1. Migrar o Angular para `/pessoas` (este repo não tem o front).
2. Rodar Flyway numa **cópia** do Postgres real e conferir o backfill
   (voluntários → `pessoa` com o mesmo `id`; responsáveis antigos →
   `pessoa` + `pessoa_relacao`; e-mails/telefones viram listas).
3. Só então apontar o Flyway de produção. V030 dropa `responsaveis` e
   as colunas soltas — não tem volta fácil.

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

## Onde está o código

```
Documents/servire/
  servire-api-back/     ← este repo
  servire-api-front/    ← Angular (outro git)
```

GitHub: [servire-api-back](https://github.com/GustavoToebe/servire-api-back),
[servire-api-front](https://github.com/GustavoToebe/servire-api-front).

Stack: Java 21 · Spring Boot 4.1.1 · Hibernate 7.4 · PostgreSQL 16 ·
Flyway V001–V031 · JJWT 0.13 · Testcontainers 2.x.

## Próximos passos

1. Front: cadastro em `/pessoas`, listas de contato, papéis múltiplos.
2. Ensaio do backfill V030/V031 numa cópia do banco real.
3. Conferir Nginx/Caddy de produção com o snippet acima.
4. Resto do billing (gateway, webhooks, bloqueio por atraso).
5. Deploy (seção 113) e corte do acesso direto do Angular ao Supabase (115).
