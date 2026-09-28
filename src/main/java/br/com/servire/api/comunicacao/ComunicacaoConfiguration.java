package br.com.servire.api.comunicacao;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra {@link WhatsappProperties} (o projeto não usa {@code @ConfigurationPropertiesScan}). */
@Configuration
@EnableConfigurationProperties(WhatsappProperties.class)
public class ComunicacaoConfiguration {
}
