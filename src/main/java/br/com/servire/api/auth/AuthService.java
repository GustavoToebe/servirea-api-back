package br.com.servire.api.auth;

import br.com.servire.api.auth.dto.TenantResumo;
import br.com.servire.api.security.JwtService;
import br.com.servire.api.security.SecurityProperties;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.web.ForbiddenException;
import br.com.servire.api.web.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquestra login/seleção de tenant/refresh/logout/forgot/reset (seção 32
 * do plano mestre). Aplica o Kill Switch (seção 28) em todo ponto onde um
 * token é emitido: usuário ativo, vínculo {@code usuario_tenant} ATIVO, e
 * status do tenant permitindo acesso (ATIVO/TRIAL — BLOQUEADO/CANCELADO
 * negam por completo, seção 131.4, sem exceção de "somente leitura").
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioTenantRepository usuarioTenantRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final SecurityProperties properties;
    private final String frontendBaseUrl;

    public AuthService(UsuarioRepository usuarioRepository,
                        UsuarioTenantRepository usuarioTenantRepository,
                        RefreshTokenService refreshTokenService,
                        PasswordResetTokenService passwordResetTokenService,
                        JwtService jwtService,
                        PasswordEncoder passwordEncoder,
                        EmailSender emailSender,
                        SecurityProperties properties,
                        @Value("${servire.frontend.base-url}") String frontendBaseUrl) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioTenantRepository = usuarioTenantRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetTokenService = passwordResetTokenService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.properties = properties;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * @throws UnauthorizedException credenciais inválidas, usuário
     * inativo, ou nenhuma paróquia ativa vinculada.
     */
    @Transactional
    public LoginResultado login(String email, String senha, String ip, String userAgent) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("E-mail ou senha inválidos."));

        // Mesma mensagem genérica para "e-mail não existe" e "senha errada"
        // — nunca revelar qual dos dois foi o motivo (evita enumeração de
        // e-mails cadastrados).
        if (usuario.getSenhaHash() == null || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new UnauthorizedException("E-mail ou senha inválidos.");
        }
        if (!usuario.isAtivo()) {
            throw new UnauthorizedException("Usuário inativo.");
        }

        List<UsuarioTenant> vinculosValidos = vinculosAtivosComTenantPermitido(usuario.getId());
        if (vinculosValidos.isEmpty()) {
            throw new UnauthorizedException("Nenhuma paróquia ativa vinculada a este usuário.");
        }

        if (vinculosValidos.size() == 1) {
            return LoginResultado.completo(emitirTokens(usuario, vinculosValidos.get(0), ip, userAgent));
        }

        String tokenSelecao = jwtService.gerarTokenSelecaoTenant(usuario.getId());
        List<TenantResumo> disponiveis = vinculosValidos.stream()
                .map(v -> TenantResumo.de(v.getTenant()))
                .toList();
        return LoginResultado.pendenteSelecao(tokenSelecao, disponiveis);
    }

    /**
     * @throws UnauthorizedException token de seleção inválido/expirado ou
     * usuário inativo.
     * @throws ForbiddenException usuário sem vínculo ativo com o tenant
     * escolhido (ou tenant bloqueado/cancelado).
     */
    @Transactional
    public TokensCompletos selecionarTenant(String tokenSelecaoTenant, UUID tenantId, String ip, String userAgent) {
        UUID usuarioId = jwtService.validarTokenSelecaoTenant(tokenSelecaoTenant);
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado."));
        if (!usuario.isAtivo()) {
            throw new UnauthorizedException("Usuário inativo.");
        }
        UsuarioTenant vinculo = vinculoAtivoComTenantPermitido(usuarioId, tenantId)
                .orElseThrow(() -> new ForbiddenException("Usuário sem acesso a esta paróquia."));
        return emitirTokens(usuario, vinculo, ip, userAgent);
    }

    /**
     * @throws UnauthorizedException refresh token desconhecido/expirado/
     * reutilizado, ou usuário inativo.
     * @throws ForbiddenException usuário sem vínculo ativo com o tenant
     * pedido.
     */
    @Transactional
    public TokensCompletos refresh(String refreshTokenBruto, UUID tenantId, String ip, String userAgent) {
        RefreshTokenService.RotacaoResultado rotacao = refreshTokenService.rotacionar(refreshTokenBruto, ip, userAgent);
        Usuario usuario = rotacao.usuario();
        if (!usuario.isAtivo()) {
            throw new UnauthorizedException("Usuário inativo.");
        }
        UsuarioTenant vinculo = vinculoAtivoComTenantPermitido(usuario.getId(), tenantId)
                .orElseThrow(() -> new ForbiddenException("Usuário sem acesso a esta paróquia."));
        String accessToken = jwtService.gerarAccessToken(usuario.getId(), tenantId, vinculo.getRole());
        return new TokensCompletos(
                accessToken, properties.jwt().accessTokenTtl().toSeconds(),
                TenantResumo.de(vinculo.getTenant()), rotacao.novoTokenBruto());
    }

    @Transactional
    public void logout(String refreshTokenBruto) {
        refreshTokenService.revogar(refreshTokenBruto);
    }

    /**
     * Sempre "sucede" do ponto de vista do chamador, exista ou não o
     * e-mail — evita enumeração de e-mails cadastrados (o controller
     * sempre responde 202 Accepted, veja {@code AuthController}).
     */
    @Transactional
    public void esqueciSenha(String email) {
        usuarioRepository.findByEmail(email).ifPresent(usuario -> {
            String token = passwordResetTokenService.gerar(usuario);
            String link = frontendBaseUrl + "/reset-password?token=" + token;
            emailSender.enviarLinkResetSenha(usuario.getEmail(), link);
        });
    }

    /**
     * @throws UnauthorizedException token de reset inválido/já usado/
     * expirado.
     */
    @Transactional
    public void redefinirSenha(String tokenBruto, String novaSenha) {
        Usuario usuario = passwordResetTokenService.consumir(tokenBruto);
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
        // Trocar a senha é um evento de segurança - mesma reação usada
        // para reuso de refresh token detectado (seção 36): encerra todas
        // as sessões ativas, forçando novo login em todos os dispositivos.
        refreshTokenService.revogarTodosDoUsuario(usuario.getId());
    }

    private List<UsuarioTenant> vinculosAtivosComTenantPermitido(UUID usuarioId) {
        return usuarioTenantRepository.findByUsuario_IdAndStatus(usuarioId, UsuarioTenant.Status.ATIVO).stream()
                .filter(v -> tenantPermiteAcesso(v.getTenant()))
                .toList();
    }

    private Optional<UsuarioTenant> vinculoAtivoComTenantPermitido(UUID usuarioId, UUID tenantId) {
        return usuarioTenantRepository.findByUsuario_IdAndTenant_Id(usuarioId, tenantId)
                .filter(v -> v.getStatus() == UsuarioTenant.Status.ATIVO)
                .filter(v -> tenantPermiteAcesso(v.getTenant()));
    }

    private boolean tenantPermiteAcesso(Tenant tenant) {
        return tenant.getStatus() == Tenant.Status.ATIVO || tenant.getStatus() == Tenant.Status.TRIAL;
    }

    private TokensCompletos emitirTokens(Usuario usuario, UsuarioTenant vinculo, String ip, String userAgent) {
        String accessToken = jwtService.gerarAccessToken(usuario.getId(), vinculo.getTenant().getId(), vinculo.getRole());
        String refreshToken = refreshTokenService.emitir(usuario, ip, userAgent);
        return new TokensCompletos(
                accessToken, properties.jwt().accessTokenTtl().toSeconds(),
                TenantResumo.de(vinculo.getTenant()), refreshToken);
    }

    public record TokensCompletos(String accessToken, long expiresInSeconds, TenantResumo tenantAtual,
                                   String refreshTokenBruto) {
    }

    public record LoginResultado(TokensCompletos completo, String tokenSelecaoTenant,
                                  List<TenantResumo> tenantsDisponiveis) {

        public static LoginResultado completo(TokensCompletos tokens) {
            return new LoginResultado(tokens, null, null);
        }

        public static LoginResultado pendenteSelecao(String tokenSelecaoTenant, List<TenantResumo> tenantsDisponiveis) {
            return new LoginResultado(null, tokenSelecaoTenant, tenantsDisponiveis);
        }

        public boolean precisaSelecionarTenant() {
            return completo == null;
        }
    }
}
