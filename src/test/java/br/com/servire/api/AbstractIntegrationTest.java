package br.com.servire.api;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base para testes de integração: sobe um PostgreSQL 16 real via
 * Testcontainers (seção 77 do plano mestre — "Integração: Testcontainers
 * PostgreSQL"), com o container compartilhado (static, "singleton
 * container pattern") entre as subclasses, para não pagar o custo de subir
 * um Postgres novo por classe de teste.
 *
 * <p>Antes do Spring Boot conectar e rodar o Flyway, aplicamos
 * {@code testcontainers/supabase-stubs.sql} — o mesmo stub usado na
 * validação manual da Fase 0/1 ({@code servire-database/local-dev}) — que
 * fornece os roles ({@code anon}/{@code authenticated}/{@code
 * service_role}) e os schemas {@code auth}/{@code storage} mínimos que as
 * migrations V005, V008, V013, V014 e V015 dependem (essas dependências só
 * existem de verdade no Supabase; aqui são um stub só para validar
 * estrutura e comportamento).</p>
 *
 * <p><b>Por que não usar {@code PostgreSQLContainer#withInitScript(...)}:</b>
 * essa API do Testcontainers faz um split ingênuo do arquivo por `;`, o que
 * quebra os blocos {@code DO $$ ... $$} do stub (que têm `;` internos nas
 * suas próprias instruções). Em vez disso, mandamos o arquivo inteiro como
 * uma única chamada JDBC ({@link Statement#execute(String)}), que é como o
 * PostgreSQL de fato entende dollar-quoting — quem faz o parsing correto é
 * o servidor, não uma ferramenta de split client-side.</p>
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final AtomicBoolean SUPABASE_STUB_APPLIED = new AtomicBoolean(false);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("servire_test")
            .withUsername("servire_test")
            .withPassword("servire_test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void applySupabaseStubOnce() throws Exception {
        // compareAndSet garante que o stub só roda uma vez mesmo com várias
        // subclasses de teste compartilhando o mesmo container estático.
        if (!SUPABASE_STUB_APPLIED.compareAndSet(false, true)) {
            return;
        }

        String stubSql;
        try (InputStream in = AbstractIntegrationTest.class.getClassLoader()
                .getResourceAsStream("testcontainers/supabase-stubs.sql")) {
            if (in == null) {
                throw new IllegalStateException(
                        "testcontainers/supabase-stubs.sql não encontrado no classpath de teste");
            }
            stubSql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(stubSql);
        }
    }
}
