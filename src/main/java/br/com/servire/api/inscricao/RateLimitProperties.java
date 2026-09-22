package br.com.servire.api.inscricao;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Rate limit do formulário público de inscrição (Fase 8, seção 45 do plano mestre). */
@ConfigurationProperties(prefix = "servire.rate-limit")
public record RateLimitProperties(@NestedConfigurationProperty InscricaoPublica inscricaoPublica) {

    public record InscricaoPublica(@DefaultValue("5") int maxTentativas, @DefaultValue("PT1H") Duration janela) {
    }
}
