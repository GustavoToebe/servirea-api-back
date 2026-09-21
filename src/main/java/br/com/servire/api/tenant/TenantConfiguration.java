package br.com.servire.api.tenant;

import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Liga o {@link ServireCurrentTenantIdentifierResolver} ao Hibernate
 * (seção 18 do plano mestre - estratégia Discriminator-based via
 * {@code @TenantId}, seção 17).
 *
 * <p>Não é necessário nenhum outro "liga multi-tenancy" - lendo o
 * código-fonte de {@code org.hibernate.cfg.MultiTenancySettings}
 * (Hibernate ORM 7.4.5.Final) confirma-se que, para a estratégia
 * baseada em discriminador/{@code @TenantId}, o único setting relevante
 * é o resolver abaixo; os demais settings dessa classe (connection
 * provider, schema mapper, credentials mapper) são só para a estratégia
 * alternativa de schema-per-tenant/database-per-tenant, que este projeto
 * não usa.</p>
 *
 * <p>Import de {@code HibernatePropertiesCustomizer} confirmado para o
 * pacote correto do Spring Boot 4.1.1 - em 3.x era
 * {@code org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer};
 * a partir do Boot 4.1.1 a classe foi movida para
 * {@code org.springframework.boot.hibernate.autoconfigure} (módulo de
 * autoconfiguração do Hibernate foi extraído em módulo próprio).</p>
 */
@Configuration
public class TenantConfiguration {

    @Bean
    public HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer(
            ServireCurrentTenantIdentifierResolver resolver) {
        return hibernateProperties -> hibernateProperties.put(
            MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
