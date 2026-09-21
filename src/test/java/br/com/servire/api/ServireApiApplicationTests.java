package br.com.servire.api;

import org.junit.jupiter.api.Test;

/**
 * Teste de fumaça: o contexto Spring sobe de ponta a ponta contra um
 * Postgres real (via {@link AbstractIntegrationTest}), o que já exercita
 * Flyway (as 15 migrations do baseline) + JPA + Actuator juntos.
 */
class ServireApiApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Se o contexto falhar ao subir (ex.: uma migration inválida, uma
        // config de datasource errada), este teste falha aqui — antes de
        // qualquer asserção mais específica dos outros testes.
    }
}
