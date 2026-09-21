package br.com.servire.api.tenant;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.voluntario.VoluntarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Teste crítico de isolamento multi-tenant (seção 78/79/80 do plano
 * mestre - "P0": tenant A nunca pode enxergar dados do tenant B, mesmo
 * tentando forçar o ID de um recurso de outro tenant).
 *
 * <p>Este teste opera no nível de repositório/Hibernate, não de
 * controller - é aqui, na camada mais baixa possível, que o mecanismo
 * {@code @TenantId} + {@link ServireCurrentTenantIdentifierResolver}
 * precisa provar que funciona; um teste só de controller não
 * distinguiria "o controller filtrou certo" de "o Hibernate filtrou
 * certo" (seção 78).</p>
 *
 * <p>Não passa pelo {@code JwtAuthenticationFilter} (que só existe no
 * pipeline de servlet de uma requisição HTTP real) - o
 * {@link TenantContext} é definido diretamente pelo teste, simulando o
 * que o filtro faria em cada requisição.</p>
 */
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    /**
     * UUID fixo do tenant inicial semeado pela migration
     * V020__seed_tenant_inicial.sql — usado aqui só como um tenant "A" já
     * existente e conhecido para o teste de isolamento; não tem mais
     * relação com autenticação desde que o {@code DevFixedTenantFilter}
     * (Fase 4) foi removido na Fase 5.
     */
    private static final UUID TENANT_A_SEED = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private VoluntarioRepository voluntarioRepository;

    @AfterEach
    void limparTenantContext() {
        // Defensivo: se alguma asserção falhar no meio do teste (exception),
        // não deixar o TenantContext vazando para o próximo teste que rodar
        // na mesma thread (mesma regra do próprio TenantContext em produção).
        TenantContext.clear();
    }

    @Test
    void tenantNaoDeveEnxergarVoluntarioDeOutroTenant() {
        UUID tenantA = TENANT_A_SEED;
        UUID tenantB = criarTenantB();

        TenantContext.set(tenantA);
        Voluntario joao = voluntarioRepository.saveAndFlush(new Voluntario("João"));
        TenantContext.clear();

        TenantContext.set(tenantB);
        Voluntario maria = voluntarioRepository.saveAndFlush(new Voluntario("Maria"));
        TenantContext.clear();

        // Tenant A: só enxerga João, mesmo em findAll().
        TenantContext.set(tenantA);
        assertThat(voluntarioRepository.findAll())
                .extracting(Voluntario::getNomeCompleto)
                .containsExactly("João");
        // Cenário P0 (seção 78): buscar pelo ID de Maria (tenant B) estando
        // no contexto do tenant A não pode retornar nada - nem revelar que
        // o recurso existe em outro tenant.
        assertThat(voluntarioRepository.findById(maria.getId())).isEmpty();
        TenantContext.clear();

        // Tenant B: só enxerga Maria, e não acha o ID de João (tenant A).
        TenantContext.set(tenantB);
        assertThat(voluntarioRepository.findAll())
                .extracting(Voluntario::getNomeCompleto)
                .containsExactly("Maria");
        assertThat(voluntarioRepository.findById(joao.getId())).isEmpty();
        TenantContext.clear();
    }

    @Test
    void salvarSemTenantContextDefinidoNaoDeveGravarLinhaComTenantErrado() {
        // Sem TenantContext.set(...), o resolver retorna o sentinela
        // SEM_TENANT (ver ServireCurrentTenantIdentifierResolver - versão
        // anterior lançava IllegalStateException aqui, mas isso quebrava a
        // própria inicialização do Spring, que sonda os repositórios antes
        // de qualquer requisição existir). O sentinela nunca existe na
        // tabela tenant, então o INSERT deve falhar por violação de foreign
        // key - nunca gravar a linha silenciosamente com um tenant errado.
        assertThat(TenantContext.get()).isNull();

        Throwable erro = catchThrowable(
                () -> voluntarioRepository.saveAndFlush(new Voluntario("Sem Tenant")));

        assertThat(erro)
                .as("INSERT sem TenantContext deveria falhar por FK violation, não suceder silenciosamente")
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private UUID criarTenantB() {
        // Tenant não é tenant-aware (é a própria raiz da hierarquia,
        // seção 17/27) - não precisa de TenantContext definido para ser
        // criado ou lido.
        Tenant tenantB = tenantRepository.saveAndFlush(
                new Tenant("TENANT-B-TESTE", "tenant-b-teste-" + UUID.randomUUID(),
                        "Paróquia B (teste de isolamento)", Tenant.Status.ATIVO));
        return tenantB.getId();
    }
}
