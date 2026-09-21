package br.com.servire.api;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base para testes de integração: sobe um PostgreSQL 16 real via
 * Testcontainers (seção 77 do plano mestre — "Integração: Testcontainers
 * PostgreSQL"), com o container compartilhado ("singleton container
 * pattern") entre TODAS as subclasses/classes de teste do módulo, para não
 * pagar o custo de subir um Postgres novo por classe de teste.
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
 * <p>Por que não usar {@code PostgreSQLContainer#withInitScript(...)}:
 * essa API do Testcontainers faz um split ingênuo do arquivo por `;`, o que
 * quebra os blocos {@code DO $$ ... $$} do stub (que têm `;` internos nas
 * suas próprias instruções). Em vez disso, mandamos o arquivo inteiro como
 * uma única chamada JDBC ({@link Statement#execute(String)}), que é como o
 * PostgreSQL de fato entende dollar-quoting — quem faz o parsing correto é
 * o servidor, não uma ferramenta de split client-side.</p>
 *
 * <p><b>Bug real encontrado e corrigido em 21/09/2026 — "singleton
 * container" que na prática NÃO era singleton:</b> a versão anterior desta
 * classe usava {@code @Testcontainers} + {@code @Container} num campo
 * estático — o padrão mais comum em tutoriais, mas que NÃO é a forma
 * correta de compartilhar um container entre várias classes de teste. A
 * extensão JUnit5 do Testcontainers gerencia o ciclo de vida de campos
 * anotados com {@code @Container} por CLASSE de teste: chama
 * {@code start()} no {@code beforeAll} e {@code stop()} no
 * {@code afterAll} — mesmo o campo sendo {@code static} e herdado de uma
 * superclasse comum. Na prática, cada nova classe de teste
 * ({@code FlywayMigrationIntegrationTest},
 * {@code ServireApiApplicationTests}, {@code TenantIsolationIntegrationTest})
 * parava o container que a classe anterior tinha acabado de usar e subia um
 * container Postgres NOVO, com uma porta mapeada diferente a cada vez
 * (confirmado no log real de {@code mvn clean verify} do usuário: portas
 * 51516, 51532 e 51535 em sequência, todas dentro da MESMA fork/JVM do
 * Surefire — não há configuração de {@code forkCount}/{@code reuseForks}
 * customizada no {@code pom.xml}, então isso não era um problema de
 * múltiplas JVMs).</p>
 *
 * <p>Isso sozinho já seria só um desperdício de tempo (subir Postgres 3x em
 * vez de 1x). O que transformou isso num bug real — e não só uma
 * ineficiência — foi a combinação com outro comportamento documentado do
 * Spring: o cache de {@code ApplicationContext} do {@code @SpringBootTest}
 * NÃO considera os valores registrados via {@code @DynamicPropertySource}
 * na chave do cache, só as anotações "estáticas" da classe (estas aqui,
 * profiles, etc.). Resultado: {@code TenantIsolationIntegrationTest} (mesma
 * configuração "estática" de {@code FlywayMigrationIntegrationTest}, sem
 * anotações extras) reaproveitou do cache o
 * {@code ApplicationContext}/{@code DataSource} que a PRIMEIRA classe já
 * tinha construído — apontando para a porta 51516, de um container que já
 * tinha sido parado — em vez de usar o container recém-criado na porta
 * 51535 especificamente para ela. Daí os erros de
 * {@code HikariPool}/conexão recusada só nessa classe.</p>
 *
 * <p>Correção: seguir à risca o padrão oficial de "singleton container" do
 * Testcontainers, que deliberadamente NÃO usa {@code @Container}/
 * {@code @Testcontainers} para este campo — o container é iniciado uma
 * única vez, manualmente, num bloco estático, e nunca é parado
 * explicitamente (quem garante a limpeza ao final do processo é o
 * container Ryuk do próprio Testcontainers, como já acontecia antes). Sem
 * {@code @Container}, a extensão JUnit5 nunca tenta parar este container
 * entre classes de teste — ele nasce uma única vez e vive até o fim do
 * processo Maven, então a porta em {@code @DynamicPropertySource} nunca
 * muda, o que também torna o comportamento do cache de contexto do Spring
 * irrelevante para este problema (cache ou não, o container é sempre o
 * mesmo).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final AtomicBoolean SUPABASE_STUB_APPLIED = new AtomicBoolean(false);

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("servire_test")
            .withUsername("servire_test")
            .withPassword("servire_test");

    static {
        // Início manual e deliberadamente sem @Container/@Testcontainers -
        // ver javadoc da classe. Este container nunca é parado
        // explicitamente; o Ryuk do Testcontainers cuida da limpeza quando
        // o processo do Maven termina.
        POSTGRES.start();
    }

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
