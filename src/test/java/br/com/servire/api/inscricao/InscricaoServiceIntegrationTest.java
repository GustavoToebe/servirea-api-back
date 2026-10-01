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
import br.com.servire.api.pessoa.PessoaService;
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
    private PessoaService pessoaService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired private br.com.servire.api.integracao.DireitosLocaisRepository direitos;

    @Test void fotoPublicaSemCotaNaoCriaInscricaoNemFazUpload() {
        Tenant tenant=criarTenant("sem-cota-foto");
        var d=new br.com.servire.api.integracao.DireitosLocais(tenant.getId(),UUID.randomUUID());
        d.setLimites("{\"armazenamento_mb\":0}");d.setSituacao("ATIVA");d.setAcessoLiberado(true);d.setConfirmadoEm(java.time.Instant.now());direitos.saveAndFlush(d);
        var foto=new org.springframework.mock.web.MockMultipartFile("foto","foto.jpg","image/jpeg",new byte[]{1,2,3});
        assertThatThrownBy(() -> inscricaoService.criarPublica(tenant.getSlug(),requestPublica("Candidato"),foto,"10.199.1.1"))
            .isInstanceOf(br.com.servire.api.minhaconta.CotaException.class);
        TenantContext.set(tenant.getId());assertThat(inscricaoRepository.count()).isZero();
        org.mockito.Mockito.verify(storageService,org.mockito.Mockito.never()).armazenar(anyString(),org.mockito.ArgumentMatchers.any(),anyString());
    }

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

        Pessoa voluntario = pessoaService.buscarPorId(aprovada.getVoluntarioId());
        assertThat(voluntario.getResponsaveis()).hasSize(1);
        assertThat(voluntario.getResponsaveis().getFirst().getResponsavel().getId()).isEqualTo(existente.getId());
        TenantContext.clear();
    }

    @Test
    void aprovarComFilhoUsandoOEmailDaMaeNaoRelacionaOVoluntarioConsigoMesmo() {
        Tenant tenant = criarTenant("email-da-mae");
        Usuario aprovador = usuarioRepository.saveAndFlush(new Usuario("aprovador-em-" + UUID.randomUUID() + "@teste.com", "Aprovador"));
        TenantContext.set(tenant.getId());
        String emailDaMae = "mae-" + UUID.randomUUID() + "@teste.com";

        Inscricao inscricao = new Inscricao("Filho Com Email Da Mae");
        InscricaoEmail emailDoFilho = new InscricaoEmail("E-mail pessoal", emailDaMae, true);
        emailDoFilho.setInscricao(inscricao);
        inscricao.getEmails().add(emailDoFilho);
        inscricao.getResponsaveis().add(responsavelComEmail(inscricao, "Mãe Do Filho", emailDaMae, true));
        inscricao = inscricaoRepository.saveAndFlush(inscricao);

        Inscricao aprovada = inscricaoService.aprovar(inscricao.getId(), aprovador.getId());

        Pessoa voluntario = pessoaService.buscarPorId(aprovada.getVoluntarioId());
        assertThat(voluntario.isResponsavel()).isFalse();
        assertThat(voluntario.getResponsaveis()).hasSize(1);
        Pessoa mae = voluntario.getResponsaveis().getFirst().getResponsavel();
        assertThat(mae.getId()).isNotEqualTo(voluntario.getId());
        assertThat(mae.getNomeCompleto()).isEqualTo("Mãe Do Filho");
    }

    @Test
    void aprovarComEmailPrincipalRepetidoPrefereQuemJaEResponsavelENaoPromoveIrmao() {
        Tenant tenant = criarTenant("email-repetido");
        Usuario aprovador = usuarioRepository.saveAndFlush(new Usuario("aprovador-rep-" + UUID.randomUUID() + "@teste.com", "Aprovador"));
        TenantContext.set(tenant.getId());
        String email = "familia-" + UUID.randomUUID() + "@teste.com";
        Pessoa irmao = pessoaRepository.saveAndFlush(pessoaComEmail(PessoaPapel.VOLUNTARIO, "Irmão Mais Velho", email));
        Pessoa mae = pessoaRepository.saveAndFlush(pessoaComEmail(PessoaPapel.RESPONSAVEL, "Mãe Da Família", email));

        Inscricao inscricao = new Inscricao("Irmão Mais Novo");
        inscricao.getResponsaveis().add(responsavelComEmail(inscricao, "Mãe Da Família", email, true));
        inscricao = inscricaoRepository.saveAndFlush(inscricao);

        Inscricao aprovada = inscricaoService.aprovar(inscricao.getId(), aprovador.getId());

        Pessoa voluntario = pessoaService.buscarPorId(aprovada.getVoluntarioId());
        assertThat(voluntario.getResponsaveis()).extracting(r -> r.getResponsavel().getId())
                .containsExactly(mae.getId());
        assertThat(pessoaRepository.findById(irmao.getId()).orElseThrow().isResponsavel()).isFalse();
    }

    @Test
    void aprovarPromoveVoluntarioAdultoAResponsavelQuandoEmailENomeBatem() {
        Tenant tenant = criarTenant("ministra-mae");
        Usuario aprovador = usuarioRepository.saveAndFlush(new Usuario("aprovador-min-" + UUID.randomUUID() + "@teste.com", "Aprovador"));
        TenantContext.set(tenant.getId());
        String email = "ministra-" + UUID.randomUUID() + "@teste.com";
        Pessoa ministra = pessoaRepository.saveAndFlush(pessoaComEmail(PessoaPapel.VOLUNTARIO, "Maria José", email));

        Inscricao inscricao = new Inscricao("Filha Da Ministra");
        inscricao.getResponsaveis().add(responsavelComEmail(inscricao, "  maria  jose ", email, true));
        inscricao = inscricaoRepository.saveAndFlush(inscricao);

        Inscricao aprovada = inscricaoService.aprovar(inscricao.getId(), aprovador.getId());

        Pessoa voluntario = pessoaService.buscarPorId(aprovada.getVoluntarioId());
        assertThat(voluntario.getResponsaveis()).extracting(r -> r.getResponsavel().getId())
                .containsExactly(ministra.getId());
        Pessoa promovida = pessoaRepository.findById(ministra.getId()).orElseThrow();
        assertThat(promovida.isVoluntario()).isTrue();
        assertThat(promovida.isResponsavel()).isTrue();
    }

    @Test
    void aprovarComPaiEMaeDividindoOMesmoEmailCriaDuasPessoas() {
        Tenant tenant = criarTenant("resp-duplicado");
        Usuario aprovador = usuarioRepository.saveAndFlush(new Usuario("aprovador-dup-" + UUID.randomUUID() + "@teste.com", "Aprovador"));
        TenantContext.set(tenant.getId());
        String email = "casal-" + UUID.randomUUID() + "@teste.com";

        Inscricao inscricao = new Inscricao("Filho Do Casal");
        inscricao.getResponsaveis().add(responsavelComEmail(inscricao, "Pai Do Casal", email, false));
        inscricao.getResponsaveis().add(responsavelComEmail(inscricao, "Mãe Do Casal", email, true));
        inscricao = inscricaoRepository.saveAndFlush(inscricao);

        Inscricao aprovada = inscricaoService.aprovar(inscricao.getId(), aprovador.getId());

        Pessoa voluntario = pessoaService.buscarPorId(aprovada.getVoluntarioId());
        assertThat(voluntario.getResponsaveis()).hasSize(2);
        assertThat(voluntario.getResponsaveis())
                .extracting(r -> r.getResponsavel().getNomeCompleto(), r -> r.isPrincipal())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Mãe Do Casal", true),
                        org.assertj.core.groups.Tuple.tuple("Pai Do Casal", false));

        // Irmão depois: cada um reencontra a própria pessoa pelo nome, sem duplicar.
        Inscricao irmao = new Inscricao("Irmão Do Casal");
        irmao.getResponsaveis().add(responsavelComEmail(irmao, "Pai Do Casal", email, true));
        irmao.getResponsaveis().add(responsavelComEmail(irmao, "Mãe Do Casal", email, false));
        irmao = inscricaoRepository.saveAndFlush(irmao);
        Pessoa voluntarioIrmao = pessoaService.buscarPorId(
                inscricaoService.aprovar(irmao.getId(), aprovador.getId()).getVoluntarioId());
        assertThat(voluntarioIrmao.getResponsaveis())
                .extracting(r -> r.getResponsavel().getId())
                .containsExactlyInAnyOrderElementsOf(
                        voluntario.getResponsaveis().stream().map(r -> r.getResponsavel().getId()).toList());
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

    private static InscricaoResponsavel responsavelComEmail(Inscricao inscricao, String nome, String email,
                                                            boolean principal) {
        InscricaoResponsavel responsavel = new InscricaoResponsavel("Mãe", nome, principal);
        responsavel.setInscricao(inscricao);
        InscricaoResponsavelEmail linha = new InscricaoResponsavelEmail("E-mail pessoal", email, true);
        linha.setResponsavel(responsavel);
        responsavel.getEmails().add(linha);
        return responsavel;
    }

    private static Pessoa pessoaComEmail(PessoaPapel papel, String nome, String email) {
        Pessoa pessoa = new Pessoa(papel, nome);
        br.com.servire.api.pessoa.PessoaEmail linha = new br.com.servire.api.pessoa.PessoaEmail(
                "E-mail pessoal", email, true);
        linha.setPessoa(pessoa);
        pessoa.getEmails().add(linha);
        if (papel == PessoaPapel.VOLUNTARIO) {
            Voluntario perfil = new Voluntario();
            perfil.setTipo(TipoVoluntario.COROINHA);
            pessoa.setVoluntario(perfil);
        }
        return pessoa;
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
                null, null, null, null, null, null, null, null, null, false, List.of(),
                null, null, null, null, false);
    }

    private InscricaoAtualizarRequest requestAtualizar(String nomeCompleto,
                                                       List<InscricaoResponsavelRequest> responsaveis) {
        return new InscricaoAtualizarRequest(
                nomeCompleto, null, null, null, null, TipoVoluntario.COROINHA,
                null, null, null, null, null, responsaveis,
                null, null, null, null, null, null, null, null, null, false, List.of(),
                null, null, null, null);
    }

    private Tenant criarTenant(String rotulo) {
        return tenantRepository.saveAndFlush(new Tenant(
                "TENANT-" + rotulo.toUpperCase() + "-TESTE", "tenant-" + rotulo + "-teste-" + UUID.randomUUID(),
                "Paróquia de teste (" + rotulo + ")", Tenant.Status.ATIVO));
    }

    @org.junit.jupiter.api.Test
    void aprovarComCpfRepetidoLancaConflictException() {
        Tenant tenant = criarTenant("cpf-repetido");
        br.com.servire.api.tenant.TenantContext.set(tenant.getId());

        br.com.servire.api.pessoa.dto.PessoaRequest pReq = new br.com.servire.api.pessoa.dto.PessoaRequest(
                java.util.Set.of(br.com.servire.api.pessoa.PessoaPapel.RESPONSAVEL), "Joao Antigo", null, null, "123.456.789-09", null,
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), null, null, null, null, null, null, null, null, null, null, null, null, null);
        pessoaService.criar(pReq);

        Inscricao inscricao = new Inscricao();
        inscricao.setNomeCompleto("Joao Novo");
        inscricao.setCpf("123.456.789-09");
        inscricao.setTipo(br.com.servire.api.voluntario.TipoVoluntario.COROINHA);
        inscricao = inscricaoRepository.save(inscricao);

        final java.util.UUID id = inscricao.getId();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> inscricaoService.aprovar(id, java.util.UUID.randomUUID()))
                .isInstanceOf(br.com.servire.api.web.ConflictException.class)
                .hasMessageContaining("com este CPF");

        Inscricao aposErro = inscricaoRepository.findById(id).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(aposErro.getStatus()).isEqualTo(br.com.servire.api.inscricao.StatusInscricao.PENDENTE);

        br.com.servire.api.tenant.TenantContext.clear();
    }

    @org.junit.jupiter.api.Test
    void publicaComCondicaoSemConsentimentoLancaBadRequest() {
        Tenant tenant = criarTenant("cuidado-bad-request");
        
        InscricaoPublicaRequest request = new InscricaoPublicaRequest(
                "token-turnstile", "Com Condicao", null, null, null, null,
                TipoVoluntario.COROINHA, null, null, null, null, null, List.of(),
                null, null, null, null, null, null, null, null, null, false, List.of(),
                List.of(br.com.servire.api.pessoa.CondicaoEspecial.TDAH), null, null, null, false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> inscricaoService.criarPublica(tenant.getSlug(), request, null, "127.0.0.1"))
                .isInstanceOf(br.com.servire.api.web.BadRequestException.class)
                .hasMessageContaining("marque a autorização");
    }

    @org.junit.jupiter.api.Test
    void publicaComConsentimentoGravaADataEAprovarCopiaParaAPessoa() {
        Tenant tenant = criarTenant("cuidado-aprovar");
        br.com.servire.api.tenant.TenantContext.set(tenant.getId());

        InscricaoPublicaRequest request = new InscricaoPublicaRequest(
                "token", "Com Consentimento", null, null, null, null,
                TipoVoluntario.COROINHA, null, null, null, null, null, List.of(),
                null, null, null, null, null, null, null, null, null, false, List.of(),
                List.of(br.com.servire.api.pessoa.CondicaoEspecial.TEA), 1, null, "Acolhimento especial", true);

        Inscricao inscricao = inscricaoService.criarPublica(tenant.getSlug(), request, null, "127.0.0.1");
        
        br.com.servire.api.tenant.TenantContext.set(tenant.getId());

        org.assertj.core.api.Assertions.assertThat(inscricao.getConsentimentoCuidadosEm()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(inscricao.getCondicoes()).containsExactly(br.com.servire.api.pessoa.CondicaoEspecial.TEA);

        br.com.servire.api.auth.Usuario aprovador = new br.com.servire.api.auth.Usuario("aprovador" + java.util.UUID.randomUUID() + "@teste.com", "Aprovador Teste");
        aprovador = usuarioRepository.saveAndFlush(aprovador);

        Inscricao aprovada = inscricaoService.aprovar(inscricao.getId(), aprovador.getId());
        Pessoa voluntario = pessoaService.buscarPorId(aprovada.getVoluntarioId());

        org.assertj.core.api.Assertions.assertThat(voluntario.getCondicoes()).containsExactly(br.com.servire.api.pessoa.CondicaoEspecial.TEA);
        org.assertj.core.api.Assertions.assertThat(voluntario.getNivelSuporteTea()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(voluntario.getCuidados()).isEqualTo("Acolhimento especial");
        
        br.com.servire.api.tenant.TenantContext.clear();
    }
}
