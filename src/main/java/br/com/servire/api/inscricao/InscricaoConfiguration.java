package br.com.servire.api.inscricao;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({TurnstileProperties.class, RateLimitProperties.class})
public class InscricaoConfiguration {
}
