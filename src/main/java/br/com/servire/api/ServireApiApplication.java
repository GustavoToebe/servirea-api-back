package br.com.servire.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da API do Servire.
 *
 * <p>Fase 2 do plano mestre (seção 102/125): esta é só a fundação —
 * health check, tratamento de exceções, logging estruturado com
 * requestId, config e Flyway/JPA plugados num banco de desenvolvimento.
 * Não há módulos de negócio (voluntário, escala, inscrição) ainda; eles
 * entram nas fases seguintes, depois do modelo SaaS lógico e do
 * TenantContext (Fases 3-4, seções 103-104).</p>
 */
@SpringBootApplication
public class ServireApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServireApiApplication.class, args);
    }
}
