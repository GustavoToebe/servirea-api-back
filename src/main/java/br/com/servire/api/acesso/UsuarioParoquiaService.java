package br.com.servire.api.acesso;

import br.com.servire.api.acesso.dto.UsuarioParoquiaRequest;
import br.com.servire.api.acesso.dto.UsuarioParoquiaResponse;
import br.com.servire.api.auth.EmailSender;
import br.com.servire.api.auth.PasswordResetTokenService;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.Formatos;
import br.com.servire.api.web.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Service
public class UsuarioParoquiaService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioTenantRepository usuarioTenantRepository;
    private final PerfilRepository perfilRepository;
    private final TenantRepository tenantRepository;
    private final PasswordResetTokenService passwordResetTokenService;
    private final EmailSender emailSender;
    private final String frontendBaseUrl;
    private final br.com.servire.api.minhaconta.CotasService cotas;

    public UsuarioParoquiaService(UsuarioRepository usuarioRepository,
                                  UsuarioTenantRepository usuarioTenantRepository,
                                  PerfilRepository perfilRepository,
                                  TenantRepository tenantRepository,
                                  PasswordResetTokenService passwordResetTokenService,
                                  EmailSender emailSender,
                                  @Value("${servire.frontend.base-url}") String frontendBaseUrl, br.com.servire.api.minhaconta.CotasService cotas) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioTenantRepository = usuarioTenantRepository;
        this.perfilRepository = perfilRepository;
        this.tenantRepository = tenantRepository;
        this.passwordResetTokenService = passwordResetTokenService;
        this.emailSender = emailSender;
        this.frontendBaseUrl = frontendBaseUrl;
        this.cotas = cotas;
    }

    @Transactional(readOnly = true)
    public List<UsuarioParoquiaResponse> listar() {
        return usuarioTenantRepository.findComUsuarioByTenant_Id(TenantContext.get()).stream()
                .map(this::resposta)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioParoquiaResponse buscar(UUID usuarioId) {
        return resposta(vinculo(usuarioId));
    }

    @Transactional
    public UsuarioParoquiaResponse criar(UsuarioParoquiaRequest request) {
        var reserva = cotas.reservar();
        UUID tenantId = TenantContext.get();
        Perfil perfil = perfilRepository.findByIdAndTenantId(request.perfilId(), tenantId)
                .orElseThrow(() -> new BadRequestException("Perfil não encontrado nesta paróquia."));
        exigirPodeUsar(perfil);
        if (!perfil.isAtivo()) {
            throw new BadRequestException("Este perfil está inativo.");
        }
        String email = request.email().trim().toLowerCase();
        Usuario existente = usuarioRepository.findByEmail(email).orElse(null);
        if (existente == null) {
            Usuario usuario = new Usuario(email, request.nome().trim());
            usuario.setTelefone(Formatos.telefone(request.telefone()));
            usuario.setTipoTelefone(request.tipoTelefone());
            usuario.setSenhaHash(null);
            usuarioRepository.saveAndFlush(usuario);
            vincular(usuario, tenantId, perfil, request.ativo());
            cotas.validar(reserva);
            String token = passwordResetTokenService.gerarConvite(usuario);
            String link = frontendBaseUrl + "/reset-password?token=" + token;
            depoisDoCommit(() -> emailSender.enviarConvite(email, link));
            return resposta(vinculo(usuario.getId()));
        }
        if (usuarioTenantRepository.findByUsuario_IdAndTenant_Id(existente.getId(), tenantId).isPresent()) {
            throw new ConflictException("Este e-mail já acessa esta paróquia.");
        }
        vincular(existente, tenantId, perfil, request.ativo());
        cotas.validar(reserva);
        String nomeParoquia = tenantRepository.findById(tenantId).map(Tenant::getNome).orElse("paróquia");
        depoisDoCommit(() -> emailSender.enviarAvisoAcesso(email, nomeParoquia));
        return resposta(vinculo(existente.getId()));
    }

    @Transactional
    public UsuarioParoquiaResponse atualizar(UUID usuarioId, UsuarioParoquiaRequest request) {
        var reserva = cotas.reservar();
        UsuarioTenant vinculo = vinculo(usuarioId);
        Perfil novo = perfilRepository.findByIdAndTenantId(request.perfilId(), vinculo.getTenant().getId())
                .orElseThrow(() -> new BadRequestException("Perfil não encontrado nesta paróquia."));
        ConcessaoDePermissao.exigirPodeAlterarAcessoTotal(
                vinculo.getPerfil() != null && vinculo.getPerfil().isAcessoTotal());
        exigirPodeUsar(novo);
        if (!novo.isAtivo()) {
            throw new BadRequestException("Este perfil está inativo.");
        }
        if (!request.ativo() || !novo.isAcessoTotal()) {
            impedirUltimoAdministrador(vinculo, novo, request.ativo());
        }
        vinculo.setPerfil(novo);
        vinculo.setRole(novo.isAcessoTotal() ? UsuarioTenant.Role.ADMIN : UsuarioTenant.Role.VISUALIZADOR);
        vinculo.setStatus(request.ativo() ? UsuarioTenant.Status.ATIVO : UsuarioTenant.Status.INATIVO);
        if (somenteEstaParoquia(usuarioId)) {
            Usuario usuario = vinculo.getUsuario();
            String email = request.email().trim().toLowerCase();
            usuarioRepository.findByEmail(email)
                    .filter(outro -> !outro.getId().equals(usuario.getId()))
                    .ifPresent(outro -> {
                        throw new ConflictException("Já existe um usuário com este e-mail.");
                    });
            usuario.setNome(request.nome().trim());
            usuario.setEmail(email);
            usuario.setTelefone(Formatos.telefone(request.telefone()));
            usuario.setTipoTelefone(request.tipoTelefone());
        }
        cotas.validar(reserva);
        return resposta(vinculo);
    }

    @Transactional
    public void reenviarConvite(UUID usuarioId) {
        UsuarioTenant vinculo = vinculo(usuarioId);
        if (vinculo.getUsuario().getSenhaHash() != null && !vinculo.getUsuario().getSenhaHash().isBlank()) {
            String token = passwordResetTokenService.gerar(vinculo.getUsuario());
            String link = frontendBaseUrl + "/reset-password?token=" + token;
            depoisDoCommit(() -> emailSender.enviarLinkResetSenha(vinculo.getUsuario().getEmail(), link));
            return;
        }
        String token = passwordResetTokenService.gerarConvite(vinculo.getUsuario());
        String link = frontendBaseUrl + "/reset-password?token=" + token;
        depoisDoCommit(() -> emailSender.enviarConvite(vinculo.getUsuario().getEmail(), link));
    }

    private void exigirPodeUsar(Perfil perfil) {
        ConcessaoDePermissao.exigirPodeConceder(perfil.isAcessoTotal(),
                perfil.getPermissoes().stream().map(PerfilPermissao::getPermissao).toList());
    }

    private void vincular(Usuario usuario, UUID tenantId, Perfil perfil, boolean ativo) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Paróquia não encontrada."));
        UsuarioTenant.Role papel = perfil.isAcessoTotal()
                ? UsuarioTenant.Role.ADMIN
                : UsuarioTenant.Role.VISUALIZADOR;
        UsuarioTenant vinculo = new UsuarioTenant(
                usuario, tenant, papel,
                ativo ? UsuarioTenant.Status.ATIVO : UsuarioTenant.Status.INATIVO);
        vinculo.setPerfil(perfil);
        usuarioTenantRepository.saveAndFlush(vinculo);
    }

    private UsuarioTenant vinculo(UUID usuarioId) {
        return usuarioTenantRepository.findComPerfilByUsuario_IdAndTenant_Id(usuarioId, TenantContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado nesta paróquia."));
    }

    private boolean somenteEstaParoquia(UUID usuarioId) {
        return usuarioTenantRepository.findByUsuario_Id(usuarioId).size() <= 1;
    }

    private void impedirUltimoAdministrador(UsuarioTenant vinculo, Perfil novo, boolean ativo) {
        boolean eraTotal = vinculo.getPerfil() != null && vinculo.getPerfil().isAcessoTotal()
                && vinculo.getStatus() == UsuarioTenant.Status.ATIVO;
        boolean continua = ativo && novo.isAcessoTotal();
        if (!eraTotal || continua) {
            return;
        }
        long restantes = usuarioTenantRepository.countByTenant_IdAndStatusAndPerfil_AcessoTotalTrue(
                vinculo.getTenant().getId(), UsuarioTenant.Status.ATIVO);
        if (restantes <= 1) {
            throw new ConflictException("A paróquia precisa de ao menos um usuário ativo com acesso total.");
        }
    }

    private UsuarioParoquiaResponse resposta(UsuarioTenant vinculo) {
        Usuario usuario = vinculo.getUsuario();
        Perfil perfil = vinculo.getPerfil();
        boolean senha = usuario.getSenhaHash() != null && !usuario.getSenhaHash().isBlank();
        return new UsuarioParoquiaResponse(
                usuario.getId(),
                vinculo.getSequencial(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTipoTelefone(),
                usuario.getTelefone(),
                perfil == null ? null : perfil.getId(),
                perfil == null ? vinculo.getRole().name() : perfil.getNome(),
                vinculo.getStatus() == UsuarioTenant.Status.ATIVO,
                senha,
                !somenteEstaParoquia(usuario.getId()),
                senha ? "SENHA_DEFINIDA" : "CONVITE_ENVIADO");
    }

    private void depoisDoCommit(Runnable envio) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                envio.run();
            }
        });
    }
}
