package br.com.servire.api.migration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.servire.api.AbstractIntegrationTest;

/**
 * Porta para JUnit das asserções estruturais que já tinham sido validadas
 * manualmente com {@code servire-database/local-dev/compare-schema.sql}
 * (Fase 0/1). Aqui o objetivo é diferente: confirmar que é o PRÓPRIO
 * Spring Boot (via {@code spring-boot-starter-flyway} embutido no starter
 * de dados), rodando a partir de {@code classpath:db/migration}, que
 * aplica corretamente as migrations do baseline (V001-V015) e das Fases
 * 3/4 (V016-V021, seção 103/104) — não só o runner bash ad hoc usado na
 * validação inicial.
 *
 * <p>Não repete TODAS as asserções do compare-schema.sql (seria
 * redundante); cobre os pontos mais críticos de segurança/integridade:
 * as 7 tabelas de domínio existem, RLS está habilitado nelas, e os grants
 * que isolam {@code criar_inscricao_impl}/{@code criar_inscricao_publica}
 * para {@code service_role} estão corretos (esse é o achado mais
 * importante do levantamento da seção 20 do documento técnico — vale ter
 * um teste automatizado que quebra o build se algum dia regredir).</p>
 *
 * <p>Total atualizado de 21 para 22 em 21/09/2026: V022 (Fase 5, seção
 * 105/32) somou a tabela {@code password_reset_token}, necessária para o
 * fluxo de "esqueci minha senha".</p>
 *
 * <p>Total atualizado de 22 para 23 em 22/09/2026: V023 (Fases 7/8/9,
 * seção 106-109) repontou as FKs {@code escalas.created_by}/
 * {@code inscricoes.aprovado_por}/{@code inscricoes.rejeitado_por} de
 * {@code auth.users} para {@code public.usuario}, e somou
 * {@code escalas.version} para controle otimista (seção 47).</p>
 *
 * <p><b>Bug real #11 (22/09/2026, teste desatualizado — encontrado pelo
 * `mvn clean verify` real da Fase 11):</b> total atualizado de 23 para 26
 * — a Fase 11 (seção 31/59/122/131.5) somou três migrations novas
 * (V024 {@code escala_vagas.presenca}, V025
 * {@code disponibilidade_voluntario}, V026 {@code audit_log}), e este
 * teste não tinha sido atualizado junto quando essas migrations foram
 * escritas (mesma categoria de esquecimento já documentada em 21→22 e
 * 22→23 acima — o contador fixo deste teste precisa ser revisado toda vez
 * que uma migration nova é somada). {@code descricoes.getLast()} também
 * mudou, de {@code "ajustes fases 7 8 9"} para {@code "table audit log"}
 * (descrição que o Flyway deriva do nome do arquivo {@code V026__table_audit_log.sql}
 * — substitui {@code _} por espaço).</p>
 *
 * <p>Total atualizado de 26 para 27 em 23/09/2026: V027 (backoffice,
 * seção 111) soma {@code usuario.operador_saas}, contato/endereço em
 * {@code tenant} e a tabela global {@code backoffice_log}.</p>
 *
 * <p>Total atualizado de 27 para 28 em 23/09/2026: V028 soma
 * {@code tenant.vigencia_ate} e {@code tenant.tipo_email} para os filtros
 * da listagem de paróquias no backoffice.</p>
 *
 * <p>Total atualizado de 28 para 29 em 23/09/2026: V029 cria o financeiro
 * manual do backoffice ({@code plano}, {@code preco_plano},
 * {@code assinatura}, {@code cobranca} — tabelas globais).</p>
 *
 * <p>Total atualizado de 29 para 30 em 24/09/2026: V030 cria o cadastro
 * pessoa-primeiro ({@code pessoa}, contatos 1:N, {@code pessoa_relacao},
 * {@code tenant_email}/{@code tenant_telefone} e o espelho da inscrição).</p>
 *
 * <p>Total atualizado de 30 para 31 em 24/09/2026: V031 troca o papel
 * exclusivo por {@code e_voluntario}/{@code e_responsavel} (papéis
 * concomitantes; responsável opcional).</p>
 *
 * <p>Total atualizado de 31 para 32 em 24/09/2026: V032 remove o tipo
 * enum {@code pessoa_papel}, órfão desde a V031.</p>
 *
 * <p>Total atualizado de 32 para 34 em 25/09/2026: V033 acrescenta
 * {@code MESC} em {@code tipo_voluntario}; V034 acrescenta mandato no
 * voluntário e a diocese com cota de servidores ativos.</p>
 */
class FlywayMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveTerAplicadoTodasAsMigrationsDoBaselineComSucesso() {
        // Não fixamos o formato exato da string de versão que o Flyway grava
        // (ex.: se normaliza "001" para "1" ou mantém os zeros à esquerda) —
        // o que importa é que as migrations rodaram e nenhuma falhou.
        // Total atualizado de 27 para 28 em 23/09/2026: V028 (filtros da
        // listagem de paróquias no backoffice). De 28 para 29 em 23/09/2026:
        // V029 (financeiro manual: plano, preco_plano, assinatura, cobranca).
        // De 29 para 30 em 24/09/2026: V030 (pessoa, contato, relação).
        // De 30 para 31 em 24/09/2026: V031 (papéis flexíveis).
        // De 31 para 32 em 24/09/2026: V032 (drop do enum pessoa_papel).
        // De 32 para 34 em 25/09/2026: V033 (MESC) e V034 (mandato e diocese).
        // De 34 para 36 em 25/09/2026: V035 (perfis e convite) e V036 (integracao v1).
        // De 36 para 37 em 25/09/2026: V037 (diocese só agrupamento, sem cota).
        // De 37 para 38 em 26/09/2026: V038 (número curto por paróquia).
        // De 38 para 39 em 27/09/2026: V039 (fecha o public para a Data API do Supabase).
        // De 39 para 40 em 28/09/2026: V040 (CPF único por paróquia).
        // De 40 para 41 em 28/09/2026: V041 (cuidado e acolhimento).
        // De 41 para 42 em 28/09/2026: V042 (layout de envio).
        // De 42 para 43 em 28/09/2026: V043 (comunicado e fila).
        // De 43 para 44 em 28/09/2026: V044 (linha de referência da escala).
        // De 44 para 45 em 28/09/2026: V045 (indisponibilidade mensal).
        // De 45 para 47 em 29/09/2026: V047 (layout escala).
        // De 47 para 48 em 29/09/2026: V048 (índices de filtro).
        // De 48 para 50 em 30/09/2026: V049 (descrição e padrão do layout) e V050 (recria escalas e layouts).
        // De 50 para 51 em 30/09/2026: V051 (remove o financeiro antigo: plano, assinatura, cobranca, backoffice_log).
        // De 51 para 52 em 01/10/2026: V052 (eventos: evento, evento_foto, evento_inscricao).
        // De 52 para 53 em 01/10/2026: V053 (evento layouts e lembretes).
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE type = 'SQL'", Integer.class);
        Integer sucesso = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE type = 'SQL' AND success = true", Integer.class);
        List<String> descricoes = jdbcTemplate.queryForList(
                "SELECT description FROM flyway_schema_history WHERE type = 'SQL' ORDER BY installed_rank",
                String.class);

        assertThat(total).isEqualTo(64);
        assertThat(sucesso).isEqualTo(64);
        assertThat(descricoes.getFirst()).isEqualTo("enums");
        assertThat(descricoes.getLast()).isEqualTo("trocas escala");

        Integer sobras = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('plano','preco_plano','assinatura','cobranca','backoffice_log')
                """, Integer.class);
        Integer operadorSaas = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'usuario' AND column_name = 'operador_saas'
                """, Integer.class);
        assertThat(sobras).isZero();
        assertThat(operadorSaas).isZero();
    }

    @Test
    void deveTerAsSeteTabelasDeDominio() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('voluntarios','pessoa','escalas','escala_eventos',
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
                  AND t.tablename IN ('voluntarios','pessoa','escalas','escala_eventos',
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

    /** V039: projeto novo do Supabase nasce pelo Flyway; nada do public pode ficar aberto para anon/authenticated. */
    @Test
    void publicFechadoParaADataApiDoSupabase() {
        List<String> semRls = jdbcTemplate.queryForList("""
                SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND NOT c.relrowsecurity
                  AND c.relname <> 'flyway_schema_history'
                """, String.class);
        Integer policies = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname = 'public'", Integer.class);
        Integer grants = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.role_table_grants
                WHERE table_schema = 'public' AND grantee IN ('anon', 'authenticated')
                """, Integer.class);
        Boolean anonChamaRpc = jdbcTemplate.queryForObject(
                "SELECT has_function_privilege('anon', 'public.criar_inscricao_publica(jsonb, jsonb)', 'EXECUTE')",
                Boolean.class);
        String opcoesDaView = jdbcTemplate.queryForObject(
                "SELECT array_to_string(reloptions, ',') FROM pg_class WHERE relname = 'vw_voluntario_compromissos'",
                String.class);

        assertThat(semRls).isEmpty();
        assertThat(policies).isZero();
        assertThat(grants).isZero();
        assertThat(anonChamaRpc).isFalse();
        assertThat(opcoesDaView).contains("security_invoker=true");
    }
}
