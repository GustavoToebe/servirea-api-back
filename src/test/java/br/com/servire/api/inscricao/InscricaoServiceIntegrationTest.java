package br.com.servire.api.inscricao;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.inscricao.dto.InscricaoAtualizarRequest;
import br.com.servire.api.inscricao.dto.InscricaoPublicaRequest;
import br.com.servire.api.inscricao.dto.InscricaoResponsavelRequest;
import br.com.servire.api.storage.StorageService;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.voluntario.VoluntarioRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import br.com.servire.api.web.TooManyRequestsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Testes de regra de negócio de {@link InscricaoService} (débito técnico
 * da Fase 8, seção 44/45/108 do plano mestre, adiado até esta rodada —
 * 22/09/2026, junto com a Fase 10).
 *
 * <p>{@code TurnstileService}/{@code StorageService} são substituídos por
 * mocks ({@code @MockitoBean}) — igual a
 * {@code VoluntarioServiceIntegrationTest} — porque nenhum dos dois está
 * configurado no profile de teste (fail-closed de propósito, ver javadoc
 * de {@link TurnstileService}); sem o mock, {@code criarPublica} sempre
 * lançaria {@code IllegalStateException} de configuração ausente antes de
 * chegar em qualquer regra de negócio.</p>
 *
 * <p>Cada teste usa um IP diferente nas chamadas a {@code criarPublica} —
 * {@link InscricaoRateLimiter} é um {@code @Component} singleton com
 * estado em memória (seção 45): reaproveitar o mesmo IP entre métodos de
 * teste diferentes contaminaria a contagem de tentativas de um teste com
 * chamadas de outro.</p>
 */
class InscricaoServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InscricaoService inscricaoService;

    @Autowired
    private InscricaoRepository inscricaoRepository;

    @Autowired
    private VoluntarioRepository voluntarioRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private TurnstileService turnstileService;

    @MockitoBean
    private StorageService storageService;

    @AfterEach
    void limparTenantContext() {
        TenantContext.clear();
    }

    @Test
    void criarPublicaComSlugValidoCriaInscricaoPendenteNoTenantResolvidoPeloSlug() {
        doNothing().when(turnstileService).validar(anyString(), anyString());
        Tenant tenant = criarTenant("slug-valido");

        Inscricao criada = inscricaoService.criarPublica(
                tenant.getSlug(), requestPublica("Candidato Teste"), null, "10.0.1.1");

        assertThat(criada.getId()).isNotNull();
        assertThat(criada.getStatus()).isEqualTo(StatusInscricao.PENDENTE);
        // criarPublica limpa o TenantContext no finally - confirmamos que
        // a inscrição foi mesmo gravada no tenant certo consultando com o
        // contexto setado manualmente (mesmo padrão de TenantIsolationIntegrationTest).
        assertThat(TenantContext.get()).isNull();
        TenantContext.set(tenant.getId());
        assertThat(inscricaoRepository.findById(criada.getId())).isPresent();
    }

    @Test
    void criarPublicaComSlugInexistenteLancaResourceNotFoundExceptionSemRevelarQualErro() {
        doNothing().when(turnstileService).validar(anyString(), anyString());

        assertThatThrownBy(() -> inscricaoService.criarPublica(
                "slug-que-nao-existe-" + UUID.randomUUID(), requestPublica("Candidato"), null, "10.0.1.2"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void criarPublicaComTenantBloqueadoLancaResourceNotFoundExceptionComoSeNaoExistisse() {
        doNothing().when(turnstileService).validar(anyString(), anyString());
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "TENANT-BLOQUEADO-TESTE", "tenant-bloqueado-teste-" + UUID.randomUUID(),
                "Paróquia bloqueada (teste)", Tenant.Status.BLOQUEADO));

        // Mesma mensagem/exceção de slug inexistente - nunca revelar que a
        // paróquia existe mas está bloqueada (mesmo espírito de não
        // enumerar e-mails cadastrados no login, seção 21/79/80).
        assertThatThrownBy(() -> inscricaoService.criarPublica(
                tenant.getSlug(), requestPublica("Candidato"), null, "10.0.1.3"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void criarPublicaSemExatamenteUmResponsavelPrincipalLancaBadRequestException() {
        doNothing().when(turnstileService).validar(anyString(), anyString());
        Tenant tenant = criarTenant("sem-principal");
        InscricaoPublicaRequest request = new InscricaoPublicaRequest(
                "token", "Candidato", null, TipoVoluntario.COROINHA,
                null, null, null, null, null, null, null, null, null, null, null,
                false, List.of(),
                List.of(new InscricaoResponsavelRequest("Mãe", "Fulana", null, null, null, false)));

        assertThatThrownBy(() -> inscricaoService.criarPublica(tenant.getSlug(), request, null, "10.0.1.4"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exatamente um responsável principal");
    }

    @Test
    void criarPublicaExcedendoLimiteDeTentativasLancaTooManyRequestsException() {
        doNothing().when(turnstileService).validar(anyString(), anyString());
        Tenant tenant = criarTenant("rate-limit");
        String ip = "10.0.1.5";

        // Padrão default é 5 tentativas/hora (servire.rate-limit.inscricao-publica) -
        // as 5 primeiras devem passar (rate limit é a PRIMEIRA barreira,
        // antes até do Turnstile mockado), a 6ª estoura o limite.
        for (int i = 0; i < 5; i++) {
            inscricaoService.criarPublica(tenant.getSlug(), requestPublica("Candidato " + i), null, ip);
        }

        assertThatThrownBy(() -> inscricaoService.criarPublica(
                tenant.getSlug(), requestPublica("Candidato Extra"), null, ip))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aprovarCriaVoluntarioRealCopiaResponsaveisEMarcaInscricaoAprovada() {
        Tenant tenant = criarTenant("aprovar");
        Usuario aprovador = usuarioRepository.saveAndFlush(new Usuario("aprovador-" + UUID.randomUUID() + "@teste.com", "Aprovador"));

        TenantContext.set(tenant.getId());
        Inscricao inscricao = new Inscricao("Candidato a Aprovar");
        InscricaoResponsavel responsavel = new InscricaoResponsavel("Mãe", "Responsável", null, null, null, true);
        responsavel.setInscricao(inscricao);
        inscricao.getResponsaveis().add(responsavel);
        inscricao = inscricaoRepository.saveAndFlush(inscricao);

        Inscricao aprovada = inscricaoService.aprovar(inscricao.getId(), aprovador.getId());

        assertThat(aprovada.getStatus()).isEqualTo(StatusInscricao.APROVADA);
        assertThat(aprovada.getAprovadoPor()).isEqualTo(aprovador.getId());
        assertThat(aprovada.getVoluntarioId()).isNotNull();
        assertThat(voluntarioRepository.findById(aprovada.getVoluntarioId()))
                .get().extracting(Voluntario::getNomeCompleto).isEqualTo("Candidato a Aprovar");
        TenantContext.clear();
    }

    @Test
    void aprovarInscricaoJaAprovadaLancaConflictException() {
        Tenant tenant = criarTenant("aprovar-duas-vezes");
        Usuario aprovador = usuarioRepository.saveAndFlush(new Usuario("aprovador2-" + UUID.randomUUID() + "@teste.com", "Aprovador"));

        TenantContext.set(tenant.getId());
        Inscricao inscricao = inscricaoComResponsavelPrincipal("Candidato Duplo");
        inscricaoService.aprovar(inscricao.getId(), aprovador.getId());

        assertThatThrownBy(() -> inscricaoService.aprovar(inscricao.getId(), aprovador.getId()))
                .isInstanceOf(ConflictException.class);
        TenantContext.clear();
    }

    @Test
    void rejeitarSemMotivoLancaBadRequestException() {
        Tenant tenant = criarTenant("rejeitar-sem-motivo");
        Usuario rejeitador = usuarioRepository.saveAndFlush(new Usuario("rejeitador-" + UUID.randomUUID() + "@teste.com", "Rejeitador"));

        TenantContext.set(tenant.getId());
        Inscricao inscricao = inscricaoComResponsavelPrincipal("Candidato Sem Motivo");

        assertThatThrownBy(() -> inscricaoService.rejeitar(inscricao.getId(), "  ", rejeitador.getId()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> inscricaoService.rejeitar(inscricao.getId(), null, rejeitador.getId()))
                .isInstanceOf(BadRequestException.class);
        TenantContext.clear();
    }

    @Test
    void rejeitarComMotivoMarcaRejeitadaERegistraMotivoEAutor() {
        Tenant tenant = criarTenant("rejeitar-com-motivo");
        Usuario rejeitador = usuarioRepository.saveAndFlush(new Usuario("rejeitador2-" + UUID.randomUUID() + "@teste.com", "Rejeitador"));

        TenantContext.set(tenant.getId());
        Inscricao inscricao = inscricaoComResponsavelPrincipal("Candidato Rejeitado");

        Inscricao rejeitada = inscricaoService.rejeitar(inscricao.getId(), "Dados incompletos", rejeitador.getId());

        assertThat(rejeitada.getStatus()).isEqualTo(StatusInscricao.REJEITADA);
        assertThat(rejeitada.getMotivoRejeicao()).isEqualTo("Dados incompletos");
        assertThat(rejeitada.getRejeitadoPor()).isEqualTo(rejeitador.getId());
        TenantContext.clear();
    }

    @Test
    void atualizarPendenteDepoisDeAprovadaLancaConflictException() {
        Tenant tenant = criarTenant("atualizar-depois-aprovada");
        Usuario aprovador = usuarioRepository.saveAndFlush(new Usuario("aprovador3-" + UUID.randomUUID() + "@teste.com", "Aprovador"));

        TenantContext.set(tenant.getId());
        Inscricao inscricao = inscricaoComResponsavelPrincipal("Candidato Já Aprovado");
        inscricaoService.aprovar(inscricao.getId(), aprovador.getId());

        InscricaoAtualizarRequest request = new InscricaoAtualizarRequest(
                "Novo Nome", null, TipoVoluntario.COROINHA,
                null, null, null, null, null, null, null, null, null, null, null,
                false, List.of(),
                List.of(new InscricaoResponsavelRequest("Mãe", "Fulana", null, null, null, true)));

        assertThatThrownBy(() -> inscricaoService.atualizarPendente(inscricao.getId(), request))
                .isInstanceOf(ConflictException.class);
        TenantContext.clear();
    }

    private Inscricao inscricaoComResponsavelPrincipal(String nome) {
        Inscricao inscricao = new Inscricao(nome);
        InscricaoResponsavel responsavel = new InscricaoResponsavel("Mãe", "Responsável de " + nome, null, null, null, true);
        responsavel.setInscricao(inscricao);
        inscricao.getResponsaveis().add(responsavel);
        return inscricaoRepository.saveAndFlush(inscricao);
    }

    private InscricaoPublicaRequest requestPublica(String nomeCompleto) {
        return new InscricaoPublicaRequest(
                "token-turnstile-qualquer", nomeCompleto, null, TipoVoluntario.COROINHA,
                null, null, null, null, null, null, null, null, null, null, null,
                false, List.of(),
                List.of(new InscricaoResponsavelRequest("Mãe", "Responsável de " + nomeCompleto, null, null, null, true)));
    }

    private Tenant criarTenant(String rotulo) {
        return tenantRepository.saveAndFlush(new Tenant(
                "TENANT-" + rotulo.toUpperCase() + "-TESTE", "tenant-" + rotulo + "-teste-" + UUID.randomUUID(),
                "Paróquia de teste (" + rotulo + ")", Tenant.Status.ATIVO));
    }
}
