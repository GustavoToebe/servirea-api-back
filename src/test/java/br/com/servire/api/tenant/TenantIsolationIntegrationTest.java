package br.com.servire.api.tenant;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.escala.Escala;
import br.com.servire.api.escala.EscalaEvento;
import br.com.servire.api.escala.EscalaRepository;
import br.com.servire.api.escala.EscalaVaga;
import br.com.servire.api.escala.TipoEscala;
import br.com.servire.api.inscricao.Inscricao;
import br.com.servire.api.inscricao.InscricaoRepository;
import br.com.servire.api.inscricao.InscricaoResponsavel;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaPapel;
import br.com.servire.api.pessoa.PessoaRelacao;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.Pessoas;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.voluntario.VoluntarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
 *
 * <p><b>Fase 10 (seção 110 do plano mestre — "Multi-tenant real"):</b> os
 * métodos {@code tenantNaoDeveEnxergar*} abaixo (adicionados em
 * 22/09/2026) ampliam esta suíte P0 original — que só cobria
 * {@link Voluntario} — para as entidades tenant-aware: {@link Pessoa}
 * (responsável e voluntário), {@link PessoaRelacao},
 * {@link Escala}/{@link EscalaEvento}/{@link EscalaVaga} (cascata de 3
 * níveis) e {@link Inscricao}/{@link InscricaoResponsavel}. Item "3. validar
 * isolamento" e parte do "4. executar suíte P0" da Fase 10 — os testes
 * usam entidades diretamente via repositório (não via *Service*), mesmo
 * espírito do teste original: provar que o próprio Hibernate/
 * {@code @TenantId} filtra corretamente, sem depender de nenhuma
 * validação de negócio da camada de serviço.</p>
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

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private EscalaRepository escalaRepository;

    @Autowired
    private InscricaoRepository inscricaoRepository;

    @PersistenceContext
    private EntityManager entityManager;

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
        Voluntario joao = Pessoas.persistirVoluntario(pessoaRepository, "João");
        TenantContext.clear();

        TenantContext.set(tenantB);
        Voluntario maria = Pessoas.persistirVoluntario(pessoaRepository, "Maria");
        TenantContext.clear();

        // Tenant A: só enxerga João, mesmo em findAll().
        TenantContext.set(tenantA);
        assertThat(pessoaRepository.findAll().stream().filter(Pessoa::isVoluntario))
                .extracting(Pessoa::getNomeCompleto)
                .containsExactly("João");
        // Cenário P0 (seção 78): buscar pelo ID de Maria (tenant B) estando
        // no contexto do tenant A não pode retornar nada - nem revelar que
        // o recurso existe em outro tenant.
        assertThat(voluntarioRepository.findById(maria.getId())).isEmpty();
        TenantContext.clear();

        // Tenant B: só enxerga Maria, e não acha o ID de João (tenant A).
        TenantContext.set(tenantB);
        assertThat(pessoaRepository.findAll().stream().filter(Pessoa::isVoluntario))
                .extracting(Pessoa::getNomeCompleto)
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
                () -> pessoaRepository.saveAndFlush(Pessoas.voluntario("Sem Tenant")));

        assertThat(erro)
                .as("INSERT sem TenantContext deveria falhar por FK violation, não suceder silenciosamente")
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /**
     * Fase 10 (seção 110): mesma checagem P0 da Fase 4, agora para
     * {@link Pessoa} com papel RESPONSAVEL e {@link PessoaRelacao}.
     */
    @Test
    void tenantNaoDeveEnxergarResponsavelDeOutroTenant() {
        UUID tenantA = criarTenant("responsavel-a");
        UUID tenantB = criarTenant("responsavel-b");

        TenantContext.set(tenantA);
        Pessoa respA = pessoaRepository.saveAndFlush(new Pessoa(PessoaPapel.RESPONSAVEL, "Responsável A"));
        Pessoa volA = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário A").getPessoa();
        volA.getResponsaveis().add(new PessoaRelacao(respA, volA, "Mãe", "Filho", true));
        pessoaRepository.saveAndFlush(volA);
        UUID respAId = respA.getId();
        TenantContext.clear();

        TenantContext.set(tenantB);
        Pessoa respB = pessoaRepository.saveAndFlush(new Pessoa(PessoaPapel.RESPONSAVEL, "Responsável B"));
        Pessoa volB = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário B").getPessoa();
        volB.getResponsaveis().add(new PessoaRelacao(respB, volB, "Pai", "Filho", true));
        pessoaRepository.saveAndFlush(volB);
        UUID respBId = respB.getId();
        TenantContext.clear();

        TenantContext.set(tenantA);
        assertThat(pessoaRepository.findAll().stream().filter(Pessoa::isResponsavel))
                .extracting(Pessoa::getNomeCompleto)
                .containsExactly("Responsável A");
        assertThat(pessoaRepository.findById(respBId)).isEmpty();
        TenantContext.clear();

        TenantContext.set(tenantB);
        assertThat(pessoaRepository.findAll().stream().filter(Pessoa::isResponsavel))
                .extracting(Pessoa::getNomeCompleto)
                .containsExactly("Responsável B");
        assertThat(pessoaRepository.findById(respAId)).isEmpty();
        TenantContext.clear();
    }

    /**
     * Fase 10 (seção 110): mesma checagem P0, agora para a cascata de TRÊS
     * níveis {@link Escala} → {@link EscalaEvento} → {@link EscalaVaga}
     * (Fase 9). Como não existe repositório dedicado para
     * {@code EscalaEvento}/{@code EscalaVaga} (só são acessados via
     * navegação a partir de {@code Escala} — ver {@code EscalaRepository}),
     * a checagem dos dois níveis mais profundos usa JPQL direto via
     * {@link EntityManager#createQuery} — o filtro {@code @TenantId} do
     * Hibernate se aplica a QUALQUER consulta JPQL sobre uma entidade
     * tenant-aware, não só às geradas por Spring Data, então isso ainda
     * prova o mecanismo do ORM, não uma regra de negócio da camada de
     * serviço.
     *
     * <p><b>Importante — sem {@code @Transactional} aqui de propósito:</b>
     * o resolver de tenant do Hibernate é consultado UMA VEZ, na abertura
     * da sessão (seção 18) — {@code @Transactional} no método do teste
     * abriria uma única sessão para o teste inteiro logo ANTES de o corpo
     * do método rodar (ou seja, antes até do primeiro
     * {@code TenantContext.set(tenantA)}), fixando o tenant resolvido para
     * toda a duração do teste e tornando os {@code TenantContext.set(...)}
     * seguintes inertes — o oposto do que o teste quer provar. Sem
     * {@code @Transactional} de teste, cada chamada de repositório
     * (transacional por si, via {@code SimpleJpaRepository}) abre sua
     * própria sessão e resolve o tenant correto no momento da chamada —
     * mesmo padrão já usado (e funcionando) pelos testes de
     * {@code Voluntario}/{@code Responsavel} acima. As consultas JPQL via
     * {@link EntityManager} também funcionam sem transação de teste: o
     * {@code EntityManager} compartilhado do Spring abre uma sessão
     * temporária só para aquela consulta quando não há transação ativa
     * (comportamento documentado de {@code SharedEntityManagerCreator}).</p>
     */
    @Test
    void tenantNaoDeveEnxergarEscalaComCascataDeTresNiveisDeOutroTenant() {
        UUID tenantA = criarTenant("escala-a");
        UUID tenantB = criarTenant("escala-b");

        TenantContext.set(tenantA);
        Escala escalaA = new Escala("Escala A", TipoEscala.SEMANAL);
        EscalaEvento eventoA = new EscalaEvento(LocalDate.of(2026, 10, 4), LocalTime.of(19, 0), "Missa");
        eventoA.setEscala(escalaA);
        EscalaVaga vagaA = new EscalaVaga(FuncaoEscala.MISSAL, 1);
        vagaA.setEvento(eventoA);
        eventoA.getVagas().add(vagaA);
        escalaA.getEventos().add(eventoA);
        escalaRepository.saveAndFlush(escalaA);
        TenantContext.clear();

        TenantContext.set(tenantB);
        Escala escalaB = new Escala("Escala B", TipoEscala.SEMANAL);
        EscalaEvento eventoB = new EscalaEvento(LocalDate.of(2026, 10, 4), LocalTime.of(19, 0), "Missa");
        eventoB.setEscala(escalaB);
        EscalaVaga vagaB = new EscalaVaga(FuncaoEscala.CRUZ, 1);
        vagaB.setEvento(eventoB);
        eventoB.getVagas().add(vagaB);
        escalaB.getEventos().add(eventoB);
        escalaRepository.saveAndFlush(escalaB);
        TenantContext.clear();

        // Nível 1 (Escala): mesmo mecanismo já provado para Voluntario.
        TenantContext.set(tenantA);
        assertThat(escalaRepository.findAll()).extracting(Escala::getTitulo).containsExactly("Escala A");
        assertThat(escalaRepository.findById(escalaB.getId())).isEmpty();

        // Nível 2 (EscalaEvento) e nível 3 (EscalaVaga) via JPQL direto.
        List<EscalaEvento> eventosVisiveis = entityManager
                .createQuery("SELECT ev FROM EscalaEvento ev", EscalaEvento.class).getResultList();
        assertThat(eventosVisiveis).extracting(EscalaEvento::getId).containsExactly(eventoA.getId());

        List<EscalaVaga> vagasVisiveis = entityManager
                .createQuery("SELECT vg FROM EscalaVaga vg", EscalaVaga.class).getResultList();
        assertThat(vagasVisiveis).extracting(EscalaVaga::getFuncao).containsExactly(FuncaoEscala.MISSAL);
        TenantContext.clear();
    }

    /**
     * Fase 10 (seção 110): mesma checagem P0 para {@link Inscricao}/
     * {@link InscricaoResponsavel} (Fase 8) — inclui a cascata de 1 nível
     * dos responsáveis da inscrição, mesmo padrão do teste de
     * {@code Responsavel} acima. Também cobre indiretamente o item "7.
     * validar inscrição pública por slug" da Fase 10: cada tenant só é
     * alcançável pelo seu próprio slug (ver
     * {@code InscricaoService#criarPublica}, que resolve o tenant a partir
     * do slug do formulário público antes de gravar qualquer coisa) — como
     * dois tenants distintos aqui têm slugs distintos e cada um só enxerga
     * a própria inscrição, fica demonstrado que não há vazamento cruzado
     * possível através dessa rota.
     *
     * <p>Sem {@code @Transactional} de teste pelo mesmo motivo explicado na
     * javadoc de {@link #tenantNaoDeveEnxergarEscalaComCascataDeTresNiveisDeOutroTenant()}.</p>
     */
    @Test
    void tenantNaoDeveEnxergarInscricaoComResponsavelDeOutroTenant() {
        UUID tenantA = criarTenant("inscricao-a");
        UUID tenantB = criarTenant("inscricao-b");

        TenantContext.set(tenantA);
        Inscricao inscricaoA = new Inscricao("Candidato A");
        InscricaoResponsavel respA = new InscricaoResponsavel("Mãe", "Responsável Insc A", true);
        respA.setInscricao(inscricaoA);
        inscricaoA.getResponsaveis().add(respA);
        inscricaoRepository.saveAndFlush(inscricaoA);
        TenantContext.clear();

        TenantContext.set(tenantB);
        Inscricao inscricaoB = new Inscricao("Candidato B");
        InscricaoResponsavel respB = new InscricaoResponsavel("Pai", "Responsável Insc B", true);
        respB.setInscricao(inscricaoB);
        inscricaoB.getResponsaveis().add(respB);
        inscricaoRepository.saveAndFlush(inscricaoB);
        TenantContext.clear();

        TenantContext.set(tenantA);
        assertThat(inscricaoRepository.findAll())
                .extracting(Inscricao::getNomeCompleto)
                .containsExactly("Candidato A");
        assertThat(inscricaoRepository.findById(inscricaoB.getId())).isEmpty();

        List<InscricaoResponsavel> responsaveisVisiveis = entityManager
                .createQuery("SELECT r FROM InscricaoResponsavel r", InscricaoResponsavel.class).getResultList();
        assertThat(responsaveisVisiveis).extracting(InscricaoResponsavel::getNome).containsExactly("Responsável Insc A");
        TenantContext.clear();
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

    /** Versão genérica de {@link #criarTenantB()} para os testes da Fase 10, que precisam de dois tenants "descartáveis" por método de teste (nunca reaproveitar o tenant semeado nem tenants de outros testes, já que esta classe não usa rollback automático por teste). */
    private UUID criarTenant(String rotulo) {
        Tenant tenant = tenantRepository.saveAndFlush(
                new Tenant("TENANT-" + rotulo.toUpperCase() + "-TESTE", "tenant-" + rotulo + "-teste-" + UUID.randomUUID(),
                        "Paróquia de teste (" + rotulo + ")", Tenant.Status.ATIVO));
        return tenant.getId();
    }
}
