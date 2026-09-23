package br.com.servire.api.backoffice;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.web.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackofficeAuthServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "SenhaForte123!";

    @Autowired
    private BackofficeAuthService backofficeAuthService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginDeOperadorDevolveAccessTokenSemTenant() {
        Usuario operador = criarOperador("login-ok");

        BackofficeAuthService.Tokens tokens = backofficeAuthService.login(
                operador.getEmail(), SENHA, "1.2.3.4", "junit");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshTokenBruto()).isNotBlank();
        assertThat(tokens.expiresInSeconds()).isGreaterThan(0);
    }

    @Test
    void padreNaoEntraNoPainelMesmoComSenhaCerta() {
        Usuario padre = novoUsuario("padre-admin");
        usuarioRepository.saveAndFlush(padre);

        assertThatThrownBy(() -> backofficeAuthService.login(padre.getEmail(), SENHA, "1.2.3.4", "junit"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("E-mail ou senha inválidos.");
    }

    @Test
    void senhaErradaDoOperadorUsaAMesmaMensagemGenerica() {
        Usuario operador = criarOperador("senha-errada");

        assertThatThrownBy(() -> backofficeAuthService.login(operador.getEmail(), "outra", "1.2.3.4", "junit"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("E-mail ou senha inválidos.");
    }

    private Usuario criarOperador(String rotulo) {
        Usuario usuario = novoUsuario(rotulo);
        usuario.setOperadorSaas(true);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private Usuario novoUsuario(String rotulo) {
        Usuario usuario = new Usuario(rotulo + "-" + UUID.randomUUID() + "@teste.com", "Usuário " + rotulo);
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        return usuario;
    }
}
