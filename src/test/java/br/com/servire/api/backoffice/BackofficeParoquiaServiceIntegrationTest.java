package br.com.servire.api.backoffice;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.AuthService;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.backoffice.dto.AtualizarParoquiaRequest;
import br.com.servire.api.backoffice.dto.CriarParoquiaRequest;
import br.com.servire.api.backoffice.dto.DashboardResponse;
import br.com.servire.api.backoffice.dto.FiltroParoquia;
import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackofficeParoquiaServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "SenhaForte123!";

    @Autowired
    private BackofficeParoquiaService backofficeParoquiaService;

    @Autowired
    private BackofficeLogService backofficeLogService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioTenantRepository usuarioTenantRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void criarParoquiaComPrimeiroAdminPersisteTenantTrialEVinculo() {
        autenticarOperador();
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        CriarParoquiaRequest request = novaParoquia("criar-" + sufixo, "admin-" + sufixo + "@teste.com");

        Tenant criado = backofficeParoquiaService.criar(request);

        assertThat(criado.getStatus()).isEqualTo(Tenant.Status.TRIAL);
        assertThat(criado.getSlug()).isEqualTo("criar-" + sufixo);
        assertThat(criado.getCidade()).isEqualTo("Curitiba");
        assertThat(criado.getVigenciaAte()).isEqualTo(java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(7));
        Usuario admin = usuarioRepository.findByEmail(request.admin().email()).orElseThrow();
        assertThat(admin.isOperadorSaas()).isFalse();
        List<UsuarioTenant> vinculos = usuarioTenantRepository.findByUsuario_Id(admin.getId());
        assertThat(vinculos).hasSize(1);
        assertThat(vinculos.getFirst().getRole()).isEqualTo(UsuarioTenant.Role.ADMIN);
        assertThat(vinculos.getFirst().getTenant().getId()).isEqualTo(criado.getId());
        assertThat(backofficeLogService.listar())
                .anyMatch(l -> "CRIAR".equals(l.getAcao()) && criado.getId().equals(l.getEntidadeId()));
    }

    @Test
    void criarParoquiaComEmailJaExistenteSoVincula() {
        autenticarOperador();
        Usuario existente = novoUsuario("ja-existe");
        usuarioRepository.saveAndFlush(existente);
        String sufixo = UUID.randomUUID().toString().substring(0, 8);

        Tenant criado = backofficeParoquiaService.criar(new CriarParoquiaRequest(
                "COD-" + sufixo, "slug-" + sufixo, "Paróquia " + sufixo,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                new CriarParoquiaRequest.AdminInicial(existente.getNome(), existente.getEmail(), SENHA)));

        assertThat(usuarioRepository.findByEmail(existente.getEmail())).hasValueSatisfying(
                u -> assertThat(u.getId()).isEqualTo(existente.getId()));
        assertThat(usuarioTenantRepository.findByUsuario_IdAndTenant_Id(existente.getId(), criado.getId())).isPresent();
    }

    @Test
    void criarParoquiaComCodigoDuplicadoLancaConflictException() {
        autenticarOperador();
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        backofficeParoquiaService.criar(novaParoquia("dup-" + sufixo, "a-" + sufixo + "@teste.com"));

        assertThatThrownBy(() -> backofficeParoquiaService.criar(
                novaParoquia("dup-" + sufixo, "b-" + sufixo + "@teste.com")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void bloquearImpedeLoginDoPadreEDesbloquearLibera() {
        autenticarOperador();
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        String emailPadre = "padre-" + sufixo + "@teste.com";
        Tenant tenant = backofficeParoquiaService.criar(novaParoquia("bloq-" + sufixo, emailPadre));

        backofficeParoquiaService.bloquear(tenant.getId());
        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().getStatus())
                .isEqualTo(Tenant.Status.BLOQUEADO);
        assertThatThrownBy(() -> authService.login(emailPadre, SENHA, "1.2.3.4", "junit"))
                .isInstanceOf(UnauthorizedException.class);

        // "Marcar como pago" saiu na V029 — pagar agora é por cobrança
        // (BillingServiceIntegrationTest); aqui fica só o desbloqueio manual.
        Tenant liberado = backofficeParoquiaService.desbloquear(tenant.getId());
        assertThat(liberado.getStatus()).isEqualTo(Tenant.Status.ATIVO);
        AuthService.LoginResultado login = authService.login(emailPadre, SENHA, "1.2.3.4", "junit");
        assertThat(login.precisaSelecionarTenant()).isFalse();
        assertThat(login.completo().tenantAtual().id()).isEqualTo(tenant.getId());
    }

    @Test
    void atualizarMudaContatoSemTocarSlugNemStatus() {
        autenticarOperador();
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = backofficeParoquiaService.criar(novaParoquia("upd-" + sufixo, "upd-" + sufixo + "@teste.com"));
        String slug = tenant.getSlug();

        Tenant atualizado = backofficeParoquiaService.atualizar(tenant.getId(), new AtualizarParoquiaRequest(
                "Nome Novo", "Razão", "00.000.000/0001-00",
                List.of(new ContatoEmailRequest("FINANCEIRO", "contato@x.com", true)),
                List.of(new ContatoTelefoneRequest("Telefone", "41 9999", true)),
                "80000-000", "Curitiba", "PR", "Centro", "Rua X", "10", "ap 1", "obs", null, null));

        assertThat(atualizado.getNome()).isEqualTo("Nome Novo");
        assertThat(atualizado.getSlug()).isEqualTo(slug);
        assertThat(atualizado.getStatus()).isEqualTo(Tenant.Status.TRIAL);
        assertThat(atualizado.getUf()).isEqualTo("PR");
        assertThat(atualizado.getEmails()).extracting(e -> e.getTipo() + ":" + e.getEmail())
                .containsExactly("FINANCEIRO:contato@x.com");
    }

    @Test
    void dashboardContaPorStatus() {
        DashboardResponse antes = backofficeParoquiaService.dashboard();
        autenticarOperador();
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        backofficeParoquiaService.criar(novaParoquia("dash-" + sufixo, "dash-" + sufixo + "@teste.com"));

        DashboardResponse depois = backofficeParoquiaService.dashboard();
        assertThat(depois.total()).isEqualTo(antes.total() + 1);
        assertThat(depois.trial()).isEqualTo(antes.trial() + 1);
    }

    @Test
    void listarFiltraPorSituacaoCnpjEmailTipoEDatas() {
        autenticarOperador();
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Tenant alvo = backofficeParoquiaService.criar(new CriarParoquiaRequest(
                "COD-F-" + sufixo, "filtro-" + sufixo, "Paróquia Filtro " + sufixo,
                null, "12.345.678/0001-90",
                List.of(new ContatoEmailRequest("CONTATO", "contato-" + sufixo + "@paroquia.com", true)),
                null, null, null, null, null, null, null, null, null, null,
                new CriarParoquiaRequest.AdminInicial("Padre F", "admin-f-" + sufixo + "@teste.com", SENHA)));
        backofficeParoquiaService.bloquear(alvo.getId());

        Tenant outro = backofficeParoquiaService.criar(novaParoquia("outro-" + sufixo, "outro-" + sufixo + "@teste.com"));

        List<Tenant> bloqueados = backofficeParoquiaService.listar(new FiltroParoquia(
                null, SituacaoParoquia.INADIMPLENTES, null, null, null, null, null, null, null, null, null));
        assertThat(bloqueados).extracting(Tenant::getId).contains(alvo.getId()).doesNotContain(outro.getId());

        List<Tenant> porCnpj = backofficeParoquiaService.listar(new FiltroParoquia(
                null, null, null, "12345678000190", null, null, null, null, null, null, null));
        assertThat(porCnpj).extracting(Tenant::getId).contains(alvo.getId());

        List<Tenant> porEmail = backofficeParoquiaService.listar(new FiltroParoquia(
                null, null, null, null, "contato-" + sufixo, null, null, null, null, null, null));
        assertThat(porEmail).extracting(Tenant::getId).contains(alvo.getId()).doesNotContain(outro.getId());

        List<Tenant> porTipo = backofficeParoquiaService.listar(new FiltroParoquia(
                null, null, null, null, null, "CONTATO", null, null, null, null, null));
        assertThat(porTipo).extracting(Tenant::getId).contains(alvo.getId());

        List<Tenant> porNome = backofficeParoquiaService.listar(new FiltroParoquia(
                null, null, "Filtro " + sufixo, null, null, null, null, null, null, null, null));
        assertThat(porNome).extracting(Tenant::getId).containsExactly(alvo.getId());

        java.time.LocalDate hoje = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        List<Tenant> contratadasHoje = backofficeParoquiaService.listar(new FiltroParoquia(
                null, null, "Filtro " + sufixo, null, null, null, hoje, hoje, null, null, null));
        assertThat(contratadasHoje).extracting(Tenant::getId).contains(alvo.getId());

        List<Tenant> vigencia = backofficeParoquiaService.listar(new FiltroParoquia(
                null, null, "Filtro " + sufixo, null, null, null, null, null, hoje, hoje.plusDays(10), null));
        assertThat(vigencia).extracting(Tenant::getId).contains(alvo.getId());
    }

    @Test
    void entrarEmSuporteEmiteTokenComClaimSuporte() {
        Usuario operador = autenticarOperador();
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = backofficeParoquiaService.criar(novaParoquia("sup-" + sufixo, "sup-" + sufixo + "@teste.com"));

        BackofficeParoquiaService.SessaoSuporte sessao = backofficeParoquiaService.entrarEmSuporte(
                tenant.getId(), "1.2.3.4", "junit");

        assertThat(sessao.accessToken()).isNotBlank();
        assertThat(sessao.tenantAtual().id()).isEqualTo(tenant.getId());
        assertThat(sessao.refreshTokenBruto()).isNotBlank();
        assertThat(backofficeLogService.listar())
                .anyMatch(l -> "SUPORTE_ENTRAR".equals(l.getAcao()) && operador.getId().equals(l.getUserId()));
    }

    private Usuario autenticarOperador() {
        Usuario operador = new Usuario("op-" + UUID.randomUUID() + "@teste.com", "Operador teste");
        operador.setSenhaHash(passwordEncoder.encode(SENHA));
        operador.setOperadorSaas(true);
        operador = usuarioRepository.saveAndFlush(operador);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.backoffice(operador.getId()),
                null,
                List.of(new SimpleGrantedAuthority("PERM_BACKOFFICE"))));
        return operador;
    }

    private Usuario novoUsuario(String rotulo) {
        Usuario usuario = new Usuario(rotulo + "-" + UUID.randomUUID() + "@teste.com", "Usuário " + rotulo);
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        return usuario;
    }

    private CriarParoquiaRequest novaParoquia(String sufixo, String emailAdmin) {
        return new CriarParoquiaRequest(
                "COD-" + sufixo, sufixo, "Paróquia " + sufixo,
                null, null, null, null, null, "Curitiba", null, null, null, null, null, null, null,
                new CriarParoquiaRequest.AdminInicial("Padre " + sufixo, emailAdmin, SENHA));
    }
}
