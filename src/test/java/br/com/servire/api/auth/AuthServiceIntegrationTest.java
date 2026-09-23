package br.com.servire.api.auth;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.security.JwtService;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.ForbiddenException;
import br.com.servire.api.web.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de {@link AuthService} (débito técnico da Fase 5, seção 32-36 do
 * plano mestre, "Task 17" — adiado repetidas vezes em favor de velocidade
 * de entrega, pago só agora, na Fase 10, junto com o resto dos testes
 * pendentes).
 *
 * <p>Roda ao nível de serviço (não HTTP/{@code MockMvc}) — a mecânica do
 * cookie HttpOnly do refresh token e a isenção de CSRF em
 * {@code /auth/login} etc. (ver javadoc de {@code AuthController}/
 * {@code SecurityConfig}) continuam SEM cobertura automatizada depois
 * desta rodada; documentado como risco residual no README/plano mestre.</p>
 *
 * <p>Usa {@link ReflectionTestUtils#setField} para forçar a expiração de
 * um token diretamente (nem {@link RefreshToken} nem
 * {@link PasswordResetToken} expõem um setter público para
 * {@code expiresAt} — de propósito, produção nunca precisa mudar essa
 * data depois de criada) — é a forma padrão do Spring de contornar isso
 * em teste sem exigir esperar 30 dias/1 hora de verdade.</p>
 *
 * <p>Os testes de {@code redefinirSenha} obtêm o token bruto chamando
 * {@link PasswordResetTokenService#gerar} diretamente, em vez de
 * {@link AuthService#esqueciSenha} — este último não devolve o valor
 * bruto ao chamador de propósito (só o {@code LoggingEmailSender}
 * "recebe" o link, e por enquanto ele só loga, ver README/plano mestre
 * sobre substituir esse remetente por um de verdade). Como o fluxo de
 * {@code esqueciSenha} já tem cobertura própria acima, gerar o token
 * direto pelo service exercita exatamente a mesma trilha de
 * hash/persistência usada em produção, sem precisar de um remetente de
 * e-mail de teste só para capturar o valor.</p>
 */
class AuthServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "SenhaForte123!";

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioTenantRepository usuarioTenantRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordResetTokenService passwordResetTokenService;

    @Test
    void loginComEmailInexistenteLancaUnauthorizedExceptionComMensagemGenerica() {
        assertThatThrownBy(() -> authService.login("nao-existe-" + UUID.randomUUID() + "@teste.com", SENHA, "1.2.3.4", "junit"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("E-mail ou senha inválidos.");
    }

    @Test
    void loginComSenhaErradaLancaUnauthorizedExceptionComAMesmaMensagemGenerica() {
        Usuario usuario = criarUsuarioComVinculoAtivo("senha-errada", Tenant.Status.ATIVO);

        // Mesma mensagem de "e-mail não existe" - nunca revelar qual dos
        // dois foi o motivo (evita enumeração de e-mails, ver javadoc de
        // AuthService.login).
        assertThatThrownBy(() -> authService.login(usuario.getEmail(), "senha-completamente-errada", "1.2.3.4", "junit"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("E-mail ou senha inválidos.");
    }

    @Test
    void loginComUsuarioInativoLancaUnauthorizedException() {
        Usuario usuario = criarUsuarioComVinculoAtivo("usuario-inativo", Tenant.Status.ATIVO);
        usuario.setAtivo(false);
        usuarioRepository.saveAndFlush(usuario);

        assertThatThrownBy(() -> authService.login(usuario.getEmail(), SENHA, "1.2.3.4", "junit"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Usuário inativo.");
    }

    @Test
    void loginComTenantBloqueadoNaoContaComoVinculoAtivoLancaUnauthorizedException() {
        // Kill switch (seção 28): mesmo com usuario_tenant ATIVO, um
        // tenant BLOQUEADO/CANCELADO nunca deve permitir login.
        Usuario usuario = criarUsuarioComVinculoAtivo("tenant-bloqueado", Tenant.Status.BLOQUEADO);

        assertThatThrownBy(() -> authService.login(usuario.getEmail(), SENHA, "1.2.3.4", "junit"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Nenhuma paróquia ativa vinculada a este usuário.");
    }

    @Test
    void loginDeOperadorNoAppDaParoquiaLancaForbiddenException() {
        Usuario operador = novoUsuario("operador-app");
        operador.setOperadorSaas(true);
        usuarioRepository.saveAndFlush(operador);

        assertThatThrownBy(() -> authService.login(operador.getEmail(), SENHA, "1.2.3.4", "junit"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Acesse o painel administrativo.");
    }

    @Test
    void loginComUmUnicoVinculoAtivoRetornaTokensCompletosDeImediato() {
        Usuario usuario = criarUsuarioComVinculoAtivo("login-simples", Tenant.Status.ATIVO);

        AuthService.LoginResultado resultado = authService.login(usuario.getEmail(), SENHA, "1.2.3.4", "junit");

        assertThat(resultado.precisaSelecionarTenant()).isFalse();
        assertThat(resultado.completo().accessToken()).isNotBlank();
        assertThat(resultado.completo().refreshTokenBruto()).isNotBlank();
    }

    @Test
    void loginComMaisDeUmVinculoAtivoRetornaPendenteDeSelecaoDeTenant() {
        Usuario usuario = usuarioRepository.saveAndFlush(novoUsuario("multi-tenant"));
        Tenant tenantA = criarTenantAtivo("multi-a");
        Tenant tenantB = criarTenantAtivo("multi-b");
        vincular(usuario, tenantA, UsuarioTenant.Role.COORDENADOR);
        vincular(usuario, tenantB, UsuarioTenant.Role.ADMIN);

        AuthService.LoginResultado resultado = authService.login(usuario.getEmail(), SENHA, "1.2.3.4", "junit");

        assertThat(resultado.precisaSelecionarTenant()).isTrue();
        assertThat(resultado.tokenSelecaoTenant()).isNotBlank();
        assertThat(resultado.tenantsDisponiveis()).hasSize(2);
    }

    @Test
    void selecionarTenantComVinculoValidoRetornaTokensCompletosDoTenantEscolhido() {
        Usuario usuario = usuarioRepository.saveAndFlush(novoUsuario("select-tenant-ok"));
        Tenant tenantA = criarTenantAtivo("select-a");
        Tenant tenantB = criarTenantAtivo("select-b");
        vincular(usuario, tenantA, UsuarioTenant.Role.COORDENADOR);
        vincular(usuario, tenantB, UsuarioTenant.Role.ADMIN);
        String tokenSelecao = jwtService.gerarTokenSelecaoTenant(usuario.getId());

        AuthService.TokensCompletos tokens = authService.selecionarTenant(tokenSelecao, tenantB.getId(), "1.2.3.4", "junit");

        assertThat(tokens.tenantAtual().id()).isEqualTo(tenantB.getId());
    }

    @Test
    void selecionarTenantSemVinculoLancaForbiddenException() {
        Usuario usuario = usuarioRepository.saveAndFlush(novoUsuario("select-tenant-sem-vinculo"));
        Tenant tenantSemVinculo = criarTenantAtivo("select-sem-vinculo");
        String tokenSelecao = jwtService.gerarTokenSelecaoTenant(usuario.getId());

        assertThatThrownBy(() -> authService.selecionarTenant(tokenSelecao, tenantSemVinculo.getId(), "1.2.3.4", "junit"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void refreshRotacionaOTokenEOAntigoDeixaDeFuncionar() {
        Usuario usuario = criarUsuarioComVinculoAtivo("refresh-rotation", Tenant.Status.ATIVO);
        AuthService.LoginResultado login = authService.login(usuario.getEmail(), SENHA, "1.2.3.4", "junit");
        String tokenOriginal = login.completo().refreshTokenBruto();
        UUID tenantId = login.completo().tenantAtual().id();

        AuthService.TokensCompletos rotacionado = authService.refresh(tokenOriginal, tenantId, "1.2.3.4", "junit");

        assertThat(rotacionado.refreshTokenBruto()).isNotEqualTo(tokenOriginal);
    }

    /**
     * Reuso de um refresh token JÁ revogado é tratado como possível roubo
     * (seção 36) — revoga TODAS as sessões ativas do usuário de uma vez.
     */
    @Test
    void reusoDeRefreshTokenJaRevogadoRevogaTodasAsSessoesDoUsuario() {
        Usuario usuario = criarUsuarioComVinculoAtivo("refresh-reuso", Tenant.Status.ATIVO);
        AuthService.LoginResultado login = authService.login(usuario.getEmail(), SENHA, "1.2.3.4", "junit");
        String tokenOriginal = login.completo().refreshTokenBruto();
        UUID tenantId = login.completo().tenantAtual().id();

        // Uma segunda sessão ativa (ex.: outro dispositivo) do MESMO usuário.
        String tokenSegundaSessao = authService.login(usuario.getEmail(), SENHA, "5.6.7.8", "outro-dispositivo")
                .completo().refreshTokenBruto();

        // Rotaciona o primeiro normalmente (token original agora revogado).
        authService.refresh(tokenOriginal, tenantId, "1.2.3.4", "junit");

        // Reapresentar o token JÁ revogado é reuso - deveria falhar E
        // revogar a segunda sessão também.
        assertThatThrownBy(() -> authService.refresh(tokenOriginal, tenantId, "9.9.9.9", "atacante"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("todas as sessões");

        assertThatThrownBy(() -> authService.refresh(tokenSegundaSessao, tenantId, "5.6.7.8", "outro-dispositivo"))
                .as("a segunda sessão deveria ter sido revogada junto, forçando novo login em todos os dispositivos")
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshComTokenExpiradoLancaUnauthorizedException() {
        Usuario usuario = criarUsuarioComVinculoAtivo("refresh-expirado", Tenant.Status.ATIVO);
        AuthService.LoginResultado login = authService.login(usuario.getEmail(), SENHA, "1.2.3.4", "junit");
        String token = login.completo().refreshTokenBruto();
        UUID tenantId = login.completo().tenantAtual().id();

        forcarExpiracaoDoUnicoRefreshTokenDoUsuario(usuario.getId());

        assertThatThrownBy(() -> authService.refresh(token, tenantId, "1.2.3.4", "junit"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token expirado.");
    }

    @Test
    void refreshComTenantSemVinculoLancaForbiddenException() {
        Usuario usuario = criarUsuarioComVinculoAtivo("refresh-sem-vinculo", Tenant.Status.ATIVO);
        AuthService.LoginResultado login = authService.login(usuario.getEmail(), SENHA, "1.2.3.4", "junit");
        Tenant outroTenant = criarTenantAtivo("refresh-outro-tenant");

        assertThatThrownBy(() -> authService.refresh(
                login.completo().refreshTokenBruto(), outroTenant.getId(), "1.2.3.4", "junit"))
                .isInstanceOf(ForbiddenException.class);
    }

    /** Sempre "sucede" do ponto de vista do chamador, mesmo para e-mail desconhecido (evita enumeração, ver javadoc de AuthService.esqueciSenha). */
    @Test
    void esqueciSenhaComEmailDesconhecidoNaoLancaExcecaoNemGeraToken() {
        long tokensAntes = passwordResetTokenRepository.count();

        authService.esqueciSenha("email-que-nao-existe-" + UUID.randomUUID() + "@teste.com");

        assertThat(passwordResetTokenRepository.count()).isEqualTo(tokensAntes);
    }

    @Test
    void esqueciSenhaComEmailConhecidoGeraTokenDeReset() {
        Usuario usuario = criarUsuarioComVinculoAtivo("esqueci-senha", Tenant.Status.ATIVO);

        authService.esqueciSenha(usuario.getEmail());

        assertThat(passwordResetTokenRepository.findAll())
                .anyMatch(t -> t.getUsuario().getId().equals(usuario.getId()));
    }

    @Test
    void redefinirSenhaComTokenValidoTrocaSenhaERevogaTodosOsRefreshTokens() {
        Usuario usuario = criarUsuarioComVinculoAtivo("redefinir-senha", Tenant.Status.ATIVO);
        String refreshTokenAntigo = authService.login(usuario.getEmail(), SENHA, "1.2.3.4", "junit")
                .completo().refreshTokenBruto();
        String tokenResetBruto = passwordResetTokenService.gerar(usuario);

        authService.redefinirSenha(tokenResetBruto, "NovaSenhaForte456!");

        Usuario atualizado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NovaSenhaForte456!", atualizado.getSenhaHash())).isTrue();
        assertThat(passwordEncoder.matches(SENHA, atualizado.getSenhaHash())).isFalse();

        // Trocar a senha é evento de segurança - revoga sessões existentes.
        assertThatThrownBy(() -> authService.refresh(refreshTokenAntigo, null, "1.2.3.4", "junit"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void redefinirSenhaComTokenJaUsadoLancaUnauthorizedException() {
        Usuario usuario = criarUsuarioComVinculoAtivo("redefinir-senha-reuso", Tenant.Status.ATIVO);
        String tokenResetBruto = passwordResetTokenService.gerar(usuario);

        authService.redefinirSenha(tokenResetBruto, "PrimeiraTroca123!");

        assertThatThrownBy(() -> authService.redefinirSenha(tokenResetBruto, "SegundaTroca456!"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("já utilizado");
    }

    // --- helpers ---------------------------------------------------------

    private Usuario criarUsuarioComVinculoAtivo(String rotulo, Tenant.Status statusTenant) {
        Usuario usuario = usuarioRepository.saveAndFlush(novoUsuario(rotulo));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "TENANT-" + rotulo.toUpperCase() + "-TESTE", "tenant-" + rotulo + "-teste-" + UUID.randomUUID(),
                "Paróquia de teste (" + rotulo + ")", statusTenant));
        vincular(usuario, tenant, UsuarioTenant.Role.COORDENADOR);
        return usuario;
    }

    private Usuario novoUsuario(String rotulo) {
        Usuario usuario = new Usuario(rotulo + "-" + UUID.randomUUID() + "@teste.com", "Usuário " + rotulo);
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        return usuario;
    }

    private Tenant criarTenantAtivo(String rotulo) {
        return tenantRepository.saveAndFlush(new Tenant(
                "TENANT-" + rotulo.toUpperCase() + "-TESTE", "tenant-" + rotulo + "-teste-" + UUID.randomUUID(),
                "Paróquia de teste (" + rotulo + ")", Tenant.Status.ATIVO));
    }

    private void vincular(Usuario usuario, Tenant tenant, UsuarioTenant.Role role) {
        usuarioTenantRepository.saveAndFlush(new UsuarioTenant(usuario, tenant, role, UsuarioTenant.Status.ATIVO));
    }

    private void forcarExpiracaoDoUnicoRefreshTokenDoUsuario(UUID usuarioId) {
        RefreshToken token = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUsuario().getId().equals(usuarioId))
                .findFirst().orElseThrow();
        ReflectionTestUtils.setField(token, "expiresAt", Instant.now().minusSeconds(60));
        refreshTokenRepository.saveAndFlush(token);
    }
}
