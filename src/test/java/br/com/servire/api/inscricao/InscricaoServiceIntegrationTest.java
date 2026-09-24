package br.com.servire.api.inscricao;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.inscricao.dto.InscricaoAtualizarRequest;
import br.com.servire.api.inscricao.dto.InscricaoPublicaRequest;
import br.com.servire.api.inscricao.dto.InscricaoResponsavelRequest;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaPapel;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
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

class InscricaoServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InscricaoService inscricaoService;

    @Autowired
    private InscricaoRepository inscricaoRepository;

    @Autowired
    private VoluntarioRepository voluntarioRepository;

    @Autowired
    private PessoaRepository pessoaRepository;

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

        assertThatThrownBy(() -> inscricaoService.criarPublica(
                tenant.getSlug(), requestPublica("Candidato"), null, "10.0.1.3"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void criarPublicaSemExatamenteUmResponsavelPrincipalLancaBadRequestException() {
        doNothing().when(turnstileService).validar(anyString(), anyString());
        Tenant tenant = criarTenant("sem-principal");
        InscricaoPublicaRequest request = requestPublicaComResponsaveis("Candidato",
                List.of(new InscricaoResponsavelRequest("Mãe", "Filho", "Fulana", null, null, false)));

        assertThatThrownBy(() -> inscricaoService.criarPublica(tenant.getSlug(), request, null, "10.0.1.4"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exatamente um principal");
    }

    @Test
    void criarPublicaSemResponsavelPersisteInscricaoDeAdulto() {
        doNothing().when(turnstileService).validar(anyString(), anyString());
        Tenant tenant = criarTenant("adulto-ministro");
        InscricaoPublicaRequest request = requestPublicaComResponsaveis("Ministro Adulto", List.of());

        Inscricao criada = inscricaoService.criarPublica(tenant.getSlug(), request, null, "10.0.1.9");

        assertThat(criada.getId()).isNotNull();
        assertThat(criada.getResponsaveis()).isEmpty();
    }

    @Test
    void criarPublicaExcedendoLimiteDeTentativasLancaTooManyRequestsException() {
        doNothing().when(turnstileService).validar(anyString(), anyString());
        Tenant tenant = criarTenant("rate-limit");
        String ip = "10.0.1.5";

        for (int i = 0; i < 5; i++) {
            inscricaoService.criarPublica(tenant.getSlug(), requestPublica("Candidato " + i), null, ip);
        }

        assertThatThrownBy(() -> inscricaoService.criarPublica(
                tenant.getSlug(), requestPublica("Candidato Extra"), null, ip))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aprovarCriaPessoaVoluntarioEMarcaInscricaoAprovada() {
        Tenant tenant = criarTenant("aprovar");
        Usuario aprovador = usuarioRepository.saveAndFlush(new Usuario("aprovador-" + UUID.randomUUID() + "@teste.com", "Aprovador"));

        TenantContext.set(tenant.getId());
        Inscricao inscricao = inscricaoComResponsavelPrincipal("Candidato a Aprovar");

        Inscricao aprovada = inscricaoService.aprovar(inscricao.getId(), aprovador.getId());

        assertThat(aprovada.getStatus()).isEqualTo(StatusInscricao.APROVADA);
        assertThat(aprovada.getAprovadoPor()).isEqualTo(aprovador.getId());
        assertThat(aprovada.getVoluntarioId()).isNotNull();
        assertThat(voluntarioRepository.findById(aprovada.getVoluntarioId()))
                .get().extracting(v -> v.getPessoa().getNomeCompleto()).isEqualTo("Candidato a Aprovar");
        assertThat(pessoaRepository.findById(aprovada.getVoluntarioId())).get()
                .extracting(Pessoa::isVoluntario).isEqualTo(true);
        TenantContext.clear();
    }

    @Test
    void aprovarReusaResponsavelPeloEmailPrincipal() {
        Tenant tenant = criarTenant("reuse-resp");
        Usuario aprovador = usuarioRepository.saveAndFlush(new Usuario("aprovador-re-" + UUID.randomUUID() + "@teste.com", "Aprovador"));
        TenantContext.set(tenant.getId());

        Pessoa existente = new Pessoa(PessoaPapel.RESPONSAVEL, "Mãe Já Cadastrada");
        br.com.servire.api.pessoa.PessoaEmail email = new br.com.servire.api.pessoa.PessoaEmail(
                "E-mail pessoal", "mae-reuse@teste.com", true);
        email.setPessoa(existente);
        existente.getEmails().add(email);
        existente = pessoaRepository.saveAndFlush(existente);

        Inscricao inscricao = new Inscricao("Filho Novo");
        InscricaoResponsavel responsavel = new InscricaoResponsavel("Mãe", "Outro Nome", true);
        responsavel.setInscricao(inscricao);
        InscricaoResponsavelEmail emailIr = new InscricaoResponsavelEmail("E-mail pessoal", "mae-reuse@teste.com", true);
        emailIr.setResponsavel(responsavel);
        responsavel.getEmails().add(emailIr);
        inscricao.getResponsaveis().add(responsavel);
        inscricao = inscricaoRepository.saveAndFlush(inscricao);

        Inscricao aprovada = inscricaoService.aprovar(inscricao.getId(), aprovador.getId());

        Pessoa voluntario = pessoaRepository.findById(aprovada.getVoluntarioId()).orElseThrow();
        assertThat(voluntario.getResponsaveis()).hasSize(1);
        assertThat(voluntario.getResponsaveis().getFirst().getResponsavel().getId()).isEqualTo(existente.getId());
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

        InscricaoAtualizarRequest request = requestAtualizar("Novo Nome",
                List.of(new InscricaoResponsavelRequest("Mãe", "Filho", "Fulana", null, null, true)));

        assertThatThrownBy(() -> inscricaoService.atualizarPendente(inscricao.getId(), request))
                .isInstanceOf(ConflictException.class);
        TenantContext.clear();
    }

    @Test
    void atualizarPendenteTrocandoOResponsavelPrincipalNaoLancaConflictException() {
        Tenant tenant = criarTenant("trocar-principal");

        TenantContext.set(tenant.getId());
        Inscricao inscricao = inscricaoComResponsavelPrincipal("Candidato Troca de Principal");

        InscricaoAtualizarRequest request = requestAtualizar(inscricao.getNomeCompleto(),
                List.of(new InscricaoResponsavelRequest("Pai", "Filho", "Novo Responsável Principal", null, null, true)));

        Inscricao atualizada = inscricaoService.atualizarPendente(inscricao.getId(), request);

        assertThat(atualizada.getResponsaveis()).hasSize(1);
        assertThat(atualizada.getResponsaveis().get(0).getNome()).isEqualTo("Novo Responsável Principal");
        assertThat(atualizada.getResponsaveis().get(0).isPrincipal()).isTrue();
        TenantContext.clear();
    }

    private Inscricao inscricaoComResponsavelPrincipal(String nome) {
        Inscricao inscricao = new Inscricao(nome);
        InscricaoResponsavel responsavel = new InscricaoResponsavel("Mãe", "Responsável de " + nome, true);
        responsavel.setInscricao(inscricao);
        inscricao.getResponsaveis().add(responsavel);
        return inscricaoRepository.saveAndFlush(inscricao);
    }

    private InscricaoPublicaRequest requestPublica(String nomeCompleto) {
        return requestPublicaComResponsaveis(nomeCompleto,
                List.of(new InscricaoResponsavelRequest("Mãe", "Filho", "Responsável de " + nomeCompleto, null, null, true)));
    }

    private InscricaoPublicaRequest requestPublicaComResponsaveis(String nomeCompleto,
                                                                  List<InscricaoResponsavelRequest> responsaveis) {
        return new InscricaoPublicaRequest(
                "token-turnstile-qualquer", nomeCompleto, null, null, null, null,
                TipoVoluntario.COROINHA, null, null, null, null, null, responsaveis,
                null, null, null, null, null, null, null, null, null, false, List.of());
    }

    private InscricaoAtualizarRequest requestAtualizar(String nomeCompleto,
                                                       List<InscricaoResponsavelRequest> responsaveis) {
        return new InscricaoAtualizarRequest(
                nomeCompleto, null, null, null, null, TipoVoluntario.COROINHA,
                null, null, null, null, null, responsaveis,
                null, null, null, null, null, null, null, null, null, false, List.of());
    }

    private Tenant criarTenant(String rotulo) {
        return tenantRepository.saveAndFlush(new Tenant(
                "TENANT-" + rotulo.toUpperCase() + "-TESTE", "tenant-" + rotulo + "-teste-" + UUID.randomUUID(),
                "Paróquia de teste (" + rotulo + ")", Tenant.Status.ATIVO));
    }
}
