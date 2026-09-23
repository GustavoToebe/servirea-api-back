package br.com.servire.api.backoffice;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BackofficeProperties.class)
public class BackofficeConfiguration {
}
