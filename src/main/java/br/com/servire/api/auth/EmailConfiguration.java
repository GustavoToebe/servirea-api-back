package br.com.servire.api.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra {@link ResendProperties} (mesmo padrão de
 * {@code StorageConfiguration}/{@code InscricaoConfiguration} — este
 * projeto não usa {@code @ConfigurationPropertiesScan}, cada grupo de
 * propriedades é habilitado explicitamente onde faz sentido).
 */
@Configuration
@EnableConfigurationProperties(ResendProperties.class)
public class EmailConfiguration {
}
