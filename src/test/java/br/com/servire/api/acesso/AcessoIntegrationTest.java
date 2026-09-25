package br.com.servire.api.acesso;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.acesso.dto.PerfilRequest;
import br.com.servire.api.acesso.dto.PerfilResponse;
import br.com.servire.api.acesso.dto.UsuarioParoquiaRequest;
import br.com.servire.api.acesso.dto.UsuarioParoquiaResponse;
import br.com.servire.api.auth.PasswordResetToken;
import br.com.servire.api.auth.PasswordResetTokenRepository;
import br.com.servire.api.auth.PasswordResetTokenService;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.security.OpaqueTokenGenerator;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regras da tela de perfis e usuários (25/09/2026): último administrador,
 * "ninguém concede mais do que tem", perfil inativo, catálogo e prazo do
 * convite. Sem autenticação no contexto a trava de concessão não se aplica
 * (chamada interna); os testes que a exercitam montam a sessão à mão.
 */
class AcessoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PerfilService perfilService;

    @Autowired
    private PerfilRepository perfilRepository;

    @Autowired
    private UsuarioParoquiaService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordResetTokenService tokenService;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    private UUID tenantId;
    private Perfil administrador;
    private Perfil secretario;
    private UsuarioParoquiaResponse admin;

    @BeforeEach
    void criarParoquia() {
        String sufixo = UUID.randomUUID().toString();
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "ACS-" + sufixo.substring(0, 8), "acesso-" + sufixo, "Paróquia acesso", Tenant.Status.ATIVO));
        tenantId = tenant.getId();
        TenantContext.set(tenantId);
        administrador = perfilService.criarPadrao(tenantId);
        secretario = perfilRepository.findByTenantIdAndNome(tenantId, PerfisPadrao.SECRETARIO).orElseThrow();
        admin = usuarioService.criar(pedido("admin", administrador.getId()));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void naoTiraOUltimoAdministradorDoPerfilComAcessoTotal() {
        assertThatThrownBy(() -> usuarioService.atualizar(admin.usuarioId(),
                pedidoDe(admin, secretario.getId(), true)))
                .isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> usuarioService.atualizar(admin.usuarioId(),
                pedidoDe(admin, administrador.getId(), false)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void quemNaoTemAcessoTotalNaoConcedeAcessoTotalNemPermissaoQueNaoTem() {
        sessaoCom("PERFIL", "PERFIL_CRIAR", "PERFIL_ALTERAR", "PESSOA");

        assertThatThrownBy(() -> perfilService.criar(new PerfilRequest("Tudo", true, true, List.of())))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> perfilService.criar(
                new PerfilRequest("Exclui", true, false, List.of("PESSOA_EXCLUIR"))))
                .isInstanceOf(ForbiddenException.class);

        PerfilResponse criado = perfilService.criar(new PerfilRequest("Leitura", true, false, List.of("PESSOA")));
        assertThat(criado.permissoes()).containsExactly("PESSOA");

        assertThatThrownBy(() -> perfilService.atualizar(administrador.getId(),
                new PerfilRequest("Administrador", true, true, List.of())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void quemSoAlteraUsuarioNaoSeMoveParaOAdministrador() {
        UsuarioParoquiaResponse maria = usuarioService.criar(pedido("maria", secretario.getId()));
        sessaoCom("USUARIO", "USUARIO_ALTERAR");

        assertThatThrownBy(() -> usuarioService.atualizar(maria.usuarioId(),
                pedidoDe(maria, administrador.getId(), true)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> usuarioService.atualizar(admin.usuarioId(),
                pedidoDe(admin, secretario.getId(), true)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void acaoTrazOModuloECodigoForaDoCatalogoEhRecusado() {
        PerfilResponse perfil = perfilService.criar(
                new PerfilRequest("Presença", true, false, List.of("VAGA_PRESENCA")));
        assertThat(perfil.permissoes()).containsExactlyInAnyOrder("VAGA", "VAGA_PRESENCA");

        assertThatThrownBy(() -> perfilService.criar(
                new PerfilRequest("Antigo", true, false, List.of("ESCALA_WRITE"))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void perfilComAcessoTotalSemUsuariosPodeSerInativado() {
        PerfilResponse perfil = perfilService.criar(new PerfilRequest("Vigário", true, true, List.of()));

        PerfilResponse atualizado = perfilService.atualizar(perfil.id(),
                new PerfilRequest("Vigário", false, true, List.of()));

        assertThat(atualizado.ativo()).isFalse();
    }

    @Test
    void naoVinculaUsuarioAPerfilInativo() {
        PerfilResponse perfil = perfilService.criar(new PerfilRequest("Parado", true, false, List.of("PESSOA")));
        perfilService.atualizar(perfil.id(), new PerfilRequest("Parado", false, false, List.of("PESSOA")));

        assertThatThrownBy(() -> usuarioService.criar(pedido("joao", perfil.id())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void conviteValeSeteDiasEResetUmaHora() {
        Usuario usuario = usuarioRepository.findById(admin.usuarioId()).orElseThrow();

        PasswordResetToken convite = token(tokenService.gerarConvite(usuario));
        PasswordResetToken reset = token(tokenService.gerar(usuario));

        assertThat(convite.getFinalidade()).isEqualTo("CONVITE");
        assertThat(convite.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(6)));
        assertThat(reset.getFinalidade()).isEqualTo("RESET");
        assertThat(reset.getExpiresAt()).isBefore(Instant.now().plus(Duration.ofHours(2)));
    }

    private PasswordResetToken token(String bruto) {
        return tokenRepository.findByTokenHash(OpaqueTokenGenerator.hash(bruto)).orElseThrow();
    }

    private void sessaoCom(String... codigos) {
        Perfil perfil = new Perfil(tenantId, "Sessão", false, false);
        perfil.substituirPermissoes(List.of(codigos));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "teste", null, PermissoesDaSessao.doPerfil(perfil)));
    }

    private UsuarioParoquiaRequest pedido(String rotulo, UUID perfilId) {
        return new UsuarioParoquiaRequest(
                "Usuário " + rotulo, rotulo + "-" + UUID.randomUUID() + "@teste.com", null, null, perfilId, true);
    }

    private UsuarioParoquiaRequest pedidoDe(UsuarioParoquiaResponse usuario, UUID perfilId, boolean ativo) {
        return new UsuarioParoquiaRequest(usuario.nome(), usuario.email(), null, null, perfilId, ativo);
    }
}
