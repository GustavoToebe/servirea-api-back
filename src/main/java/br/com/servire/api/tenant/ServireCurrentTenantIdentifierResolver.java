package br.com.servire.api.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ponte entre o {@link TenantContext} da requisição e o Hibernate
 * (seção 18 do plano mestre): toda sessão do Hibernate pergunta a este
 * resolver qual é o tenant atual, e entidades marcadas com
 * {@code @TenantId} recebem automaticamente {@code WHERE tenant_id = ?}
 * (leitura) e o valor certo de {@code tenant_id} (escrita).
 *
 * <p>Fluxo completo (seção 18): JWT -&gt; Security Filter -&gt; tenantId
 * -&gt; {@link TenantContext} -&gt; este resolver -&gt; Hibernate Session.
 * Desde a Fase 5, quem popula o {@link TenantContext} a cada requisição é
 * o {@code br.com.servire.api.security.JwtAuthenticationFilter}, a partir
 * do tenantId validado no access token.</p>
 *
 * <p>Wiring: ver {@link TenantConfiguration}, que registra este bean via
 * {@code hibernate.tenant_identifier_resolver}
 * ({@code MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER}).</p>
 *
 * <p><b>Por que este método NUNCA lança exceção (mudança de 21/09/2026 -
 * bug real encontrado em `mvn clean verify`):</b> a primeira versão deste
 * resolver lançava {@code IllegalStateException} quando
 * {@link TenantContext#get()} retornava {@code null}, com a intenção de
 * falhar alto e cedo em caso de bug de programação. Na prática, isso
 * quebrou a inicialização inteira da aplicação: o Spring Data JPA sonda
 * cada repositório por named queries já na criação do bean (método
 * {@code NamedQuery.hasNamedQuery}, chamado durante
 * {@code preInstantiateSingletons}), e essa sondagem cria um
 * {@code EntityManager} - o que, sob um {@code SessionFactory}
 * multi-tenant, sempre resolve o tenant atual, mesmo para entidades
 * globais sem {@code @TenantId} (como {@link Tenant}/{@link Usuario}) e
 * muito antes de existir qualquer requisição HTTP. Ou seja, este método é
 * chamado em momentos do ciclo de vida do Spring que não têm - e não
 * precisam ter - um {@link TenantContext} definido.</p>
 *
 * <p>A solução adotada: retornar um UUID sentinela reservado
 * ({@link #SEM_TENANT}, o UUID nulo {@code 00000000-...-000000000000}) em
 * vez de lançar exceção. Esse UUID nunca existe na tabela {@code tenant}
 * (nenhuma migration o insere), então continua havendo uma rede de
 * segurança P0 real: qualquer tentativa de INSERT/UPDATE de verdade numa
 * entidade tenant-aware sem {@link TenantContext} definido ainda falha -
 * só que agora com uma violação de foreign key
 * ({@code voluntarios_tenant_id_fkey} e equivalentes) em vez de uma
 * exceção lançada aqui, que quebrava até operações inofensivas (como esta
 * sondagem de metadata do Spring, que nunca chega a executar SQL de
 * verdade). Uma leitura (`findAll`) sem contexto simplesmente filtra por
 * um tenant_id que não existe e retorna vazio - fecha o vazamento sem
 * derrubar a aplicação.</p>
 */
@Component
public class ServireCurrentTenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    /**
     * UUID nulo usado como tenant "de sistema" quando nenhum
     * {@link TenantContext} está definido (fora de uma requisição HTTP).
     * Nunca é inserido na tabela {@code tenant} - qualquer INSERT/UPDATE
     * real numa tabela tenant-aware usando este valor viola a foreign key
     * de {@code tenant_id}, então continua falhando, só que no banco em
     * vez de aqui.
     */
    public static final UUID SEM_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID tenantId = TenantContext.get();
        return tenantId != null ? tenantId : SEM_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
