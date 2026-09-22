package br.com.servire.api.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra {@link StorageProperties} (mesmo padrão de
 * {@link br.com.servire.api.tenant.TenantConfiguration}/
 * {@code SecurityConfig} — este projeto não usa
 * {@code @ConfigurationPropertiesScan}, cada grupo de propriedades é
 * habilitado explicitamente onde faz sentido).
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {
}
