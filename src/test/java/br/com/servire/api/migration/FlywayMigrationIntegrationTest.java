package br.com.servire.api.migration;

import br.com.servire.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Porta para JUnit das asserções estruturais que já tinham sido validadas
 * manualmente com {@code servire-database/local-dev/compare-schema.sql}
 * (Fase 0/1). Aqui o objetivo é diferente: confirmar que é o PRÓPRIO
 * Spring Boot (via {@code spring-boot-starter-flyway} embutido no starter
 * de dados), rodando a partir de {@code classpath:db/migration}, que
 * aplica corretamente as 15 migrations do baseline — não só o runner bash
 * ad hoc usado na validação inicial.
 *
 * <p>Não repete TODAS as asserções do compare-schema.sql (seria
 * redundante); cobre os pontos mais críticos de segurança/integridade:
 * as 7 tabelas de domínio existem, RLS está habilitado nelas, e os grants
 * que isolam {@code criar_inscricao_impl}/{@code criar_inscricao_publica}
 * para {@code service_role} estão corretos (esse é o achado mais
 * importante do levantamento da seção 20 do documento técnico — vale ter
 * um teste automatizado que quebra o build se algum dia regredir).</p>
 */
class FlywayMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveTerAplicadoTodasAs15MigrationsDoBaselineComSucesso() {
        // Não fixamos o formato exato da string de versão que o Flyway grava
        // (ex.: se normaliza "001" para "1" ou mantém os zeros à esquerda) —
        // o que importa é que as 15 migrations rodaram e nenhuma falhou.
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE type = 'SQL'", Integer.class);
        Integer sucesso = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE type = 'SQL' AND success = true", Integer.class);
        List<String> descricoes = jdbcTemplate.queryForList(
                "SELECT description FROM flyway_schema_history WHERE type = 'SQL' ORDER BY installed_rank",
                String.class);

        assertThat(total).isEqualTo(15);
        assertThat(sucesso).isEqualTo(15);
        assertThat(descricoes.getFirst()).isEqualTo("enums");
        assertThat(descricoes.getLast()).isEqualTo("storage bucket");
    }

    @Test
    void deveTerAsSeteTabelasDeDominio() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('voluntarios','responsaveis','escalas','escala_eventos',
                                      'escala_vagas','inscricoes','inscricao_responsaveis')
                """, Integer.class);
        assertThat(count).isEqualTo(7);
    }

    @Test
    void devetTerRlsHabilitadoNasSeteTabelas() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_tables t
                JOIN pg_class c ON c.relname = t.tablename
                JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = t.schemaname
                WHERE t.schemaname = 'public'
                  AND t.tablename IN ('voluntarios','responsaveis','escalas','escala_eventos',
                                       'escala_vagas','inscricoes','inscricao_responsaveis')
                  AND c.relrowsecurity = true
                """, Integer.class);
        assertThat(count).isEqualTo(7);
    }

    @Test
    void criarInscricaoDeveSerExecutavelSomentePorServiceRole() {
        Boolean serviceRoleCanExecute = jdbcTemplate.queryForObject(
                "SELECT has_function_privilege('service_role', 'private.criar_inscricao_impl(jsonb, jsonb)', 'EXECUTE')",
                Boolean.class);
        Boolean authenticatedCanExecute = jdbcTemplate.queryForObject(
                "SELECT has_function_privilege('authenticated', 'private.criar_inscricao_impl(jsonb, jsonb)', 'EXECUTE')",
                Boolean.class);
        Boolean anonCanExecute = jdbcTemplate.queryForObject(
                "SELECT has_function_privilege('anon', 'private.criar_inscricao_impl(jsonb, jsonb)', 'EXECUTE')",
                Boolean.class);

        assertThat(serviceRoleCanExecute).as("service_role deve poder criar inscrição").isTrue();
        assertThat(authenticatedCanExecute).as("authenticated NÃO deve poder criar inscrição direto").isFalse();
        assertThat(anonCanExecute).as("anon NÃO deve poder criar inscrição direto").isFalse();
    }

    @Test
    void aprovarERejeitarInscricaoDevemExigirAuthenticated() {
        Boolean authenticatedCanApprove = jdbcTemplate.queryForObject(
                "SELECT has_function_privilege('authenticated', 'private.aprovar_inscricao_impl(uuid)', 'EXECUTE')",
                Boolean.class);
        Boolean authenticatedCanReject = jdbcTemplate.queryForObject(
                "SELECT has_function_privilege('authenticated', 'private.rejeitar_inscricao_impl(uuid, text)', 'EXECUTE')",
                Boolean.class);

        assertThat(authenticatedCanApprove).isTrue();
        assertThat(authenticatedCanReject).isTrue();
    }
}
