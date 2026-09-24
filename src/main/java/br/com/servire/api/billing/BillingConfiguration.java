package br.com.servire.api.billing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Financeiro manual (V029). O {@link Clock} fica no fuso do Brasil: "hoje"
 * do vencimento é o dia da paróquia, não o dia UTC (a partir das 21h em
 * Brasília o UTC já virou o dia e uma cobrança venceria antes da hora).
 */
@Configuration
@EnableScheduling
public class BillingConfiguration {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock servireClock() {
        return Clock.system(FUSO);
    }
}
