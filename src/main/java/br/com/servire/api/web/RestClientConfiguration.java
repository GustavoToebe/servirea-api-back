package br.com.servire.api.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Declara o bean {@code RestClient.Builder} explicitamente — necessário
 * para {@code SupabaseStorageService} (Fase 7) e {@code TurnstileService}
 * (Fase 8), que o injetam no construtor.
 *
 * <p><b>Bug real #6 (22/09/2026), confirmado pelo primeiro
 * {@code mvn clean verify} rodado depois das Fases 7/8/9:</b> o contexto
 * Spring falhou ao subir em TODOS os testes que carregam a aplicação
 * inteira, com
 * {@code NoSuchBeanDefinitionException: No qualifying bean of type
 * 'org.springframework.web.client.RestClient$Builder'}. Confirma o risco
 * residual já documentado no README/plano mestre ao escrever
 * {@code SupabaseStorageService}: diferente do que a javadoc daquela
 * classe supunha, {@code spring-boot-starter-web} sozinho NÃO auto-configura
 * um bean {@code RestClient.Builder} neste projeto (Spring Boot 4.1.1) —
 * a auto-configuration correspondente não é ativada só pelo starter web
 * (mesma categoria de surpresa já vivida com
 * {@code HibernatePropertiesCustomizer}/{@code spring-boot-starter-flyway},
 * onde uma peça de infraestrutura que "deveria vir de graça" no Boot 3
 * precisou ser resolvida à mão no Boot 4.1 — ver javadoc de
 * {@code TenantConfiguration}). A correção mais simples e robusta é
 * declarar o bean manualmente aqui, em vez de depender de qual módulo do
 * Boot 4 traz (ou não) essa auto-configuration.</p>
 */
@Configuration
public class RestClientConfiguration {

    @Bean
    public RestClient.Builder restClientBuilder() {
        var cliente=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        var fabrica=new org.springframework.http.client.JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(java.time.Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(fabrica);
    }
}
