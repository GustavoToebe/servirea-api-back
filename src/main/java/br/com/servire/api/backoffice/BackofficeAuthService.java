package br.com.servire.api.backoffice;

import br.com.servire.api.auth.RefreshTokenService;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.security.JwtService;
import br.com.servire.api.security.SecurityProperties;
import br.com.servire.api.web.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login do operador no painel (seção 111). Recusa quem não é
 * {@code operador_saas} com a mesma mensagem genérica de senha errada —
 * um padre tentando {@code /admin/auth/login} não descobre se o e-mail
 * existe no sistema.
 */
@Service
public class BackofficeAuthService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties properties;

    public BackofficeAuthService(UsuarioRepository usuarioRepository,
                                  RefreshTokenService refreshTokenService,
                                  JwtService jwtService,
                                  PasswordEncoder passwordEncoder,
                                  SecurityProperties properties) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public Tokens login(String email, String senha, String ip, String userAgent) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("E-mail ou senha inválidos."));
        if (usuario.getSenhaHash() == null || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new UnauthorizedException("E-mail ou senha inválidos.");
        }
        if (!usuario.isAtivo() || !usuario.isOperadorSaas()) {
            throw new UnauthorizedException("E-mail ou senha inválidos.");
        }
        return emitir(usuario, ip, userAgent);
    }

    @Transactional
    public Tokens refresh(String refreshTokenBruto, String ip, String userAgent) {
        RefreshTokenService.RotacaoResultado rotacao = refreshTokenService.rotacionar(refreshTokenBruto, ip, userAgent);
        Usuario usuario = rotacao.usuario();
        if (!usuario.isAtivo() || !usuario.isOperadorSaas()) {
            throw new UnauthorizedException("Usuário inativo.");
        }
        String accessToken = jwtService.gerarBackofficeToken(usuario.getId());
        return new Tokens(accessToken, properties.jwt().accessTokenTtl().toSeconds(), rotacao.novoTokenBruto());
    }

    @Transactional
    public void logout(String refreshTokenBruto) {
        refreshTokenService.revogar(refreshTokenBruto);
    }

    private Tokens emitir(Usuario usuario, String ip, String userAgent) {
        String accessToken = jwtService.gerarBackofficeToken(usuario.getId());
        String refreshToken = refreshTokenService.emitir(usuario, ip, userAgent);
        return new Tokens(accessToken, properties.jwt().accessTokenTtl().toSeconds(), refreshToken);
    }

    public record Tokens(String accessToken, long expiresInSeconds, String refreshTokenBruto) {
    }
}
