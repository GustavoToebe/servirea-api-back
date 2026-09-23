package br.com.servire.api.backoffice;

import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.backoffice.dto.AtualizarUsuarioRequest;
import br.com.servire.api.backoffice.dto.CriarUsuarioRequest;
import br.com.servire.api.backoffice.dto.SubstituirVinculosRequest;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Logins da paróquia (padre/coordenador/visualizador) no painel. Não
 * lista nem edita {@code operador_saas} — o operador não é um usuário de
 * paróquia (seção 60).
 */
@Service
public class BackofficeUsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioTenantRepository usuarioTenantRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final BackofficeLogService backofficeLogService;

    public BackofficeUsuarioService(UsuarioRepository usuarioRepository,
                                     UsuarioTenantRepository usuarioTenantRepository,
                                     TenantRepository tenantRepository,
                                     PasswordEncoder passwordEncoder,
                                     BackofficeLogService backofficeLogService) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioTenantRepository = usuarioTenantRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.backofficeLogService = backofficeLogService;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar(Boolean ativo, String busca) {
        return usuarioRepository.findAll(filtro(ativo, busca));
    }

    @Transactional(readOnly = true)
    public Usuario buscar(UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        if (usuario.isOperadorSaas()) {
            throw new ResourceNotFoundException("Usuário não encontrado.");
        }
        return usuario;
    }

    @Transactional(readOnly = true)
    public List<UsuarioTenant> vinculosDe(UUID usuarioId) {
        buscar(usuarioId);
        return usuarioTenantRepository.findByUsuario_Id(usuarioId);
    }

    @Transactional
    public Usuario criar(CriarUsuarioRequest request) {
        String email = request.email().trim();
        if (usuarioRepository.existsByEmail(email)) {
            throw new ConflictException("Já existe um usuário com este e-mail.");
        }
        Usuario usuario = new Usuario(email, request.nome().trim());
        usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        usuario.setAtivo(request.ativo() == null || request.ativo());
        try {
            usuario = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Já existe um usuário com este e-mail.");
        }
        if (request.vinculos() != null) {
            aplicarVinculos(usuario, request.vinculos().stream()
                    .map(v -> new SubstituirVinculosRequest.Vinculo(v.tenantId(), v.role(), v.status()))
                    .toList());
        }
        backofficeLogService.registrar("CRIAR", "USUARIO", usuario.getId(), null);
        return usuario;
    }

    @Transactional
    public Usuario atualizar(UUID id, AtualizarUsuarioRequest request) {
        Usuario usuario = buscar(id);
        String email = request.email().trim();
        if (!email.equals(usuario.getEmail()) && usuarioRepository.existsByEmail(email)) {
            throw new ConflictException("Já existe um usuário com este e-mail.");
        }
        usuario.setNome(request.nome().trim());
        usuario.setEmail(email);
        if (request.senha() != null && !request.senha().isBlank()) {
            usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        }
        usuario.setAtivo(request.ativo());
        backofficeLogService.registrar("ATUALIZAR", "USUARIO", usuario.getId(), null);
        return usuario;
    }

    @Transactional
    public List<UsuarioTenant> substituirVinculos(UUID usuarioId, List<SubstituirVinculosRequest.Vinculo> vinculos) {
        Usuario usuario = buscar(usuarioId);
        aplicarVinculos(usuario, vinculos);
        backofficeLogService.registrar("VINCULOS", "USUARIO", usuario.getId(), null);
        return usuarioTenantRepository.findByUsuario_Id(usuarioId);
    }

    private void aplicarVinculos(Usuario usuario, List<SubstituirVinculosRequest.Vinculo> vinculos) {
        List<UsuarioTenant> atuais = usuarioTenantRepository.findByUsuario_Id(usuario.getId());
        Set<UUID> desejados = vinculos.stream().map(SubstituirVinculosRequest.Vinculo::tenantId)
                .collect(Collectors.toCollection(HashSet::new));
        if (desejados.size() != vinculos.size()) {
            throw new BadRequestException("A lista de vínculos não pode repetir a mesma paróquia.");
        }

        List<UsuarioTenant> paraRemover = atuais.stream()
                .filter(v -> !desejados.contains(v.getTenant().getId()))
                .toList();
        if (!paraRemover.isEmpty()) {
            usuarioTenantRepository.deleteAll(paraRemover);
            usuarioTenantRepository.flush();
        }

        for (SubstituirVinculosRequest.Vinculo item : vinculos) {
            Tenant tenant = tenantRepository.findById(item.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Paróquia não encontrada."));
            UsuarioTenant.Status status = item.status() == null ? UsuarioTenant.Status.ATIVO : item.status();
            UsuarioTenant existente = atuais.stream()
                    .filter(v -> v.getTenant().getId().equals(item.tenantId()))
                    .findFirst()
                    .orElse(null);
            if (existente != null) {
                existente.setRole(item.role());
                existente.setStatus(status);
            } else {
                usuarioTenantRepository.save(new UsuarioTenant(usuario, tenant, item.role(), status));
            }
        }
    }

    private static Specification<Usuario> filtro(Boolean ativo, String busca) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.isFalse(root.get("operadorSaas")));
            if (ativo != null) {
                predicados.add(cb.equal(root.get("ativo"), ativo));
            }
            if (busca != null && !busca.isBlank()) {
                String like = "%" + busca.trim().toLowerCase() + "%";
                predicados.add(cb.or(
                        cb.like(cb.lower(root.get("nome")), like),
                        cb.like(cb.lower(root.get("email")), like)));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }
}
