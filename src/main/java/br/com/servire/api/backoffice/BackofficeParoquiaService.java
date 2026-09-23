package br.com.servire.api.backoffice;

import br.com.servire.api.auth.RefreshTokenService;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.auth.dto.TenantResumo;
import br.com.servire.api.backoffice.dto.AtualizarParoquiaRequest;
import br.com.servire.api.backoffice.dto.CriarParoquiaRequest;
import br.com.servire.api.backoffice.dto.DashboardResponse;
import br.com.servire.api.backoffice.dto.FiltroParoquia;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.security.JwtService;
import br.com.servire.api.security.SecurityProperties;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Paróquias no painel do operador (seção 71/111). Tabela {@code tenant}
 * é global — sem {@code TenantContext}. Criar paróquia + primeiro admin
 * numa transação só.
 */
@Service
public class BackofficeParoquiaService {

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioTenantRepository usuarioTenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final BackofficeLogService backofficeLogService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityProperties properties;

    public BackofficeParoquiaService(TenantRepository tenantRepository,
                                      UsuarioRepository usuarioRepository,
                                      UsuarioTenantRepository usuarioTenantRepository,
                                      PasswordEncoder passwordEncoder,
                                      BackofficeLogService backofficeLogService,
                                      JwtService jwtService,
                                      RefreshTokenService refreshTokenService,
                                      SecurityProperties properties) {
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioTenantRepository = usuarioTenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.backofficeLogService = backofficeLogService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        return new DashboardResponse(
                tenantRepository.count(),
                tenantRepository.countByStatus(Tenant.Status.ATIVO),
                tenantRepository.countByStatus(Tenant.Status.TRIAL),
                tenantRepository.countByStatus(Tenant.Status.BLOQUEADO),
                tenantRepository.countByStatus(Tenant.Status.CANCELADO));
    }

    /** Trial do MVP (seção 131.3) — gravado em {@code vigencia_ate} na criação. */
    static final int DIAS_TRIAL = 7;

    @Transactional(readOnly = true)
    public List<Tenant> listar(FiltroParoquia filtro) {
        FiltroParoquia efetivo = filtro == null
                ? new FiltroParoquia(null, null, null, null, null, null, null, null, null, null)
                : filtro;
        return tenantRepository.findAll(filtroSpec(efetivo));
    }

    @Transactional(readOnly = true)
    public Tenant buscar(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paróquia não encontrada."));
    }

    @Transactional
    public Tenant criar(CriarParoquiaRequest request) {
        String codigo = request.codigo().trim();
        String slug = request.slug().trim().toLowerCase();
        if (tenantRepository.existsByCodigo(codigo)) {
            throw new ConflictException("Já existe uma paróquia com este código.");
        }
        if (tenantRepository.existsBySlug(slug)) {
            throw new ConflictException("Já existe uma paróquia com este slug.");
        }

        Tenant tenant = new Tenant(codigo, slug, request.nome().trim(), Tenant.Status.TRIAL);
        tenant.setVigenciaAte(LocalDate.now(ZoneOffset.UTC).plusDays(DIAS_TRIAL));
        aplicarContato(tenant, request.razaoSocial(), request.cnpj(), request.email(), request.telefone(),
                request.cep(), request.cidade(), request.uf(), request.bairro(),
                request.logradouro(), request.numero(), request.complemento(), request.observacoes(),
                request.tipoEmail());
        try {
            tenant = tenantRepository.saveAndFlush(tenant);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Já existe uma paróquia com este código ou slug.");
        }

        Usuario admin = garantirAdmin(request.admin(), tenant);
        usuarioTenantRepository.save(new UsuarioTenant(
                admin, tenant, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO));

        backofficeLogService.registrar("CRIAR", "TENANT", tenant.getId(), tenant.getId());
        return tenant;
    }

    @Transactional
    public Tenant atualizar(UUID id, AtualizarParoquiaRequest request) {
        Tenant tenant = buscar(id);
        tenant.setNome(request.nome().trim());
        aplicarContato(tenant, request.razaoSocial(), request.cnpj(), request.email(), request.telefone(),
                request.cep(), request.cidade(), request.uf(), request.bairro(),
                request.logradouro(), request.numero(), request.complemento(), request.observacoes(),
                request.tipoEmail());
        if (request.vigenciaAte() != null) {
            tenant.setVigenciaAte(request.vigenciaAte());
        }
        backofficeLogService.registrar("ATUALIZAR", "TENANT", tenant.getId(), tenant.getId());
        return tenant;
    }

    @Transactional
    public Tenant bloquear(UUID id) {
        Tenant tenant = buscar(id);
        tenant.setStatus(Tenant.Status.BLOQUEADO);
        backofficeLogService.registrar("BLOQUEAR", "TENANT", tenant.getId(), tenant.getId());
        return tenant;
    }

    @Transactional
    public Tenant desbloquear(UUID id) {
        Tenant tenant = buscar(id);
        tenant.setStatus(Tenant.Status.ATIVO);
        backofficeLogService.registrar("DESBLOQUEAR", "TENANT", tenant.getId(), tenant.getId());
        return tenant;
    }

    @Transactional
    public Tenant marcarPago(UUID id) {
        Tenant tenant = buscar(id);
        if (tenant.getStatus() == Tenant.Status.CANCELADO) {
            throw new BadRequestException("Paróquia cancelada não pode ser marcada como paga.");
        }
        tenant.setStatus(Tenant.Status.ATIVO);
        tenant.setUltimoPagamentoEm(Instant.now());
        backofficeLogService.registrar("MARCAR_PAGO", "TENANT", tenant.getId(), tenant.getId());
        return tenant;
    }

    /**
     * Emite access token da paróquia com {@code suporte=true} e um refresh
     * para o cookie {@code /auth} (o app da paróquia renova por lá).
     */
    @Transactional
    public SessaoSuporte entrarEmSuporte(UUID id, String ip, String userAgent) {
        Tenant tenant = buscar(id);
        UUID operadorId = operadorAtualId();
        Usuario operador = usuarioRepository.findById(operadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Operador não encontrado."));
        String accessToken = jwtService.gerarAccessToken(
                operador.getId(), tenant.getId(), UsuarioTenant.Role.ADMIN, true);
        String refreshToken = refreshTokenService.emitir(operador, ip, userAgent);
        backofficeLogService.registrar("SUPORTE_ENTRAR", "TENANT", tenant.getId(), tenant.getId());
        return new SessaoSuporte(
                accessToken,
                properties.jwt().accessTokenTtl().toSeconds(),
                TenantResumo.de(tenant),
                refreshToken);
    }

    private Usuario garantirAdmin(CriarParoquiaRequest.AdminInicial admin, Tenant tenant) {
        return usuarioRepository.findByEmail(admin.email().trim()).map(existente -> {
            if (existente.isOperadorSaas()) {
                throw new ConflictException("Este e-mail pertence a um operador da plataforma.");
            }
            if (usuarioTenantRepository.findByUsuario_IdAndTenant_Id(existente.getId(), tenant.getId()).isPresent()) {
                throw new ConflictException("Este usuário já está vinculado a esta paróquia.");
            }
            if (!existente.isAtivo()) {
                existente.setAtivo(true);
            }
            if (existente.getSenhaHash() == null) {
                existente.setSenhaHash(passwordEncoder.encode(admin.senha()));
            }
            return existente;
        }).orElseGet(() -> {
            Usuario novo = new Usuario(admin.email().trim(), admin.nome().trim());
            novo.setSenhaHash(passwordEncoder.encode(admin.senha()));
            return usuarioRepository.saveAndFlush(novo);
        });
    }

    private static void aplicarContato(Tenant tenant, String razaoSocial, String cnpj, String email, String telefone,
                                        String cep, String cidade, String uf, String bairro, String logradouro,
                                        String numero, String complemento, String observacoes, String tipoEmail) {
        tenant.setRazaoSocial(opcional(razaoSocial));
        tenant.setCnpj(opcional(cnpj));
        tenant.setEmail(opcional(email));
        tenant.setTelefone(opcional(telefone));
        tenant.setCep(opcional(cep));
        tenant.setCidade(opcional(cidade));
        tenant.setUf(opcional(uf));
        tenant.setBairro(opcional(bairro));
        tenant.setLogradouro(opcional(logradouro));
        tenant.setNumero(opcional(numero));
        tenant.setComplemento(opcional(complemento));
        tenant.setObservacoes(opcional(observacoes));
        tenant.setTipoEmail(opcional(tipoEmail) == null ? null : opcional(tipoEmail).toUpperCase());
    }

    private static Specification<Tenant> filtroSpec(FiltroParoquia filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (filtro.status() != null) {
                predicados.add(cb.equal(root.get("status"), filtro.status()));
            } else if (filtro.situacao() != null) {
                predicados.add(root.get("status").in(filtro.situacao().status()));
            }
            if (filtro.nome() != null && !filtro.nome().isBlank()) {
                String like = "%" + filtro.nome().trim().toLowerCase() + "%";
                predicados.add(cb.or(
                        cb.like(cb.lower(root.get("nome")), like),
                        cb.like(cb.lower(root.get("codigo")), like),
                        cb.like(cb.lower(root.get("slug")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("razaoSocial"), "")), like)));
            }
            if (filtro.cnpj() != null && !filtro.cnpj().isBlank()) {
                String bruto = filtro.cnpj().trim().toLowerCase();
                String soDigitos = bruto.replaceAll("\\D", "");
                Predicate likeBruto = cb.like(cb.lower(cb.coalesce(root.get("cnpj"), "")), "%" + bruto + "%");
                if (soDigitos.isEmpty()) {
                    predicados.add(likeBruto);
                } else {
                    var soDigitosColuna = cb.function(
                            "regexp_replace",
                            String.class,
                            cb.coalesce(root.get("cnpj"), cb.literal("")),
                            cb.literal("[^0-9]"),
                            cb.literal(""),
                            cb.literal("g"));
                    predicados.add(cb.or(
                            likeBruto,
                            cb.like(soDigitosColuna, "%" + soDigitos + "%")));
                }
            }
            if (filtro.email() != null && !filtro.email().isBlank()) {
                predicados.add(cb.like(
                        cb.lower(cb.coalesce(root.get("email"), "")),
                        "%" + filtro.email().trim().toLowerCase() + "%"));
            }
            if (filtro.tipoEmail() != null && !filtro.tipoEmail().isBlank()) {
                predicados.add(cb.equal(
                        cb.upper(cb.coalesce(root.get("tipoEmail"), "")),
                        filtro.tipoEmail().trim().toUpperCase()));
            }
            if (filtro.contratadoDe() != null) {
                predicados.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        filtro.contratadoDe().atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (filtro.contratadoAte() != null) {
                predicados.add(cb.lessThan(
                        root.get("createdAt"),
                        filtro.contratadoAte().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (filtro.vigenciaDe() != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("vigenciaAte"), filtro.vigenciaDe()));
            }
            if (filtro.vigenciaAte() != null) {
                predicados.add(cb.lessThanOrEqualTo(root.get("vigenciaAte"), filtro.vigenciaAte()));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    private static String opcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private UUID operadorAtualId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser usuario) {
            return usuario.usuarioId();
        }
        throw new ResourceNotFoundException("Operador não autenticado.");
    }

    public record SessaoSuporte(String accessToken, long expiresInSeconds, TenantResumo tenantAtual,
                                 String refreshTokenBruto) {
    }
}
