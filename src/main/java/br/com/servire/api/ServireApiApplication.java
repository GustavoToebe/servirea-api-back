package br.com.servire.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Ponto de entrada da API do Servirea.
 *
 * <p>Fase 2 do plano mestre (seção 102/125): esta é só a fundação —
 * health check, tratamento de exceções, logging estruturado com
 * requestId, config e Flyway/JPA plugados num banco de desenvolvimento.
 * Não há módulos de negócio (voluntário, escala, inscrição) ainda; eles
 * entram nas fases seguintes, depois do modelo SaaS lógico e do
 * TenantContext (Fases 3-4, seções 103-104).</p>
 *
 * <p>Sem o usuário em memória do Spring: o login é só por JWT (e HMAC na
 * integração), e o usuário padrão só gerava o aviso "Using generated
 * security password" no log de produção (27/09/2026).</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ServireApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServireApiApplication.class, args);
    }
}
