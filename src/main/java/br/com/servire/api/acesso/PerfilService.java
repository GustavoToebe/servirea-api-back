package br.com.servire.api.acesso;

import br.com.servire.api.acesso.dto.PerfilRequest;
import br.com.servire.api.acesso.dto.PerfilResponse;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class PerfilService {

    private final PerfilRepository perfilRepository;
    private final UsuarioTenantRepository usuarioTenantRepository;

    public PerfilService(PerfilRepository perfilRepository, UsuarioTenantRepository usuarioTenantRepository) {
        this.perfilRepository = perfilRepository;
        this.usuarioTenantRepository = usuarioTenantRepository;
    }

    @Transactional(readOnly = true)
    public List<PerfilResponse> listar() {
        UUID tenantId = TenantContext.get();
        return perfilRepository.findByTenantIdOrderByNomeAsc(tenantId).stream()
                .map(perfil -> PerfilResponse.de(perfil, usuarios(perfil.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PerfilResponse buscar(UUID id) {
        return PerfilResponse.de(carregar(id), usuarios(id));
    }

    @Transactional
    public PerfilResponse criar(PerfilRequest request) {
        UUID tenantId = TenantContext.get();
        if (perfilRepository.findByTenantIdAndNome(tenantId, request.nome().trim()).isPresent()) {
            throw new ConflictException("Já existe um perfil com este nome.");
        }
        ConcessaoDePermissao.exigirPodeConceder(request.acessoTotal(), normalizar(request.permissoes()));
        Perfil perfil = new Perfil(tenantId, request.nome().trim(), request.acessoTotal(), false);
        aplicar(perfil, request);
        perfilRepository.saveAndFlush(perfil);
        return PerfilResponse.de(perfil, 0);
    }

    @Transactional
    public PerfilResponse atualizar(UUID id, PerfilRequest request) {
        Perfil perfil = carregar(id);
        ConcessaoDePermissao.exigirPodeAlterarAcessoTotal(perfil.isAcessoTotal());
        ConcessaoDePermissao.exigirPodeConceder(request.acessoTotal(), normalizar(request.permissoes()));
        if (perfil.isSistema()) {
            if (!request.acessoTotal() || !request.ativo()) {
                throw new BadRequestException("O perfil Administrador permanece ativo e com todas as opções liberadas.");
            }
        }
        if (!request.ativo() && perfil.isAtivo() && usuarios(id) > 0) {
            throw new ConflictException("Mova os usuários antes de inativar este perfil.");
        }
        if (perfil.isAcessoTotal() && (!request.acessoTotal() || !request.ativo())) {
            garantirAdministradorRestante(perfil.getTenantId(), id);
        }
        perfil.setNome(request.nome().trim());
        aplicar(perfil, request);
        return PerfilResponse.de(perfil, usuarios(id));
    }

    @Transactional
    public PerfilResponse duplicar(UUID id) {
        Perfil origem = carregar(id);
        ConcessaoDePermissao.exigirPodeConceder(origem.isAcessoTotal() && !origem.isSistema(), codigos(origem));
        String nome = origem.getNome() + " (cópia)";
        Perfil copia = new Perfil(origem.getTenantId(), nome, false, false);
        copia.setAcessoTotal(origem.isAcessoTotal() && !origem.isSistema());
        if (!copia.isAcessoTotal()) {
            copia.substituirPermissoes(codigos(origem));
        }
        perfilRepository.saveAndFlush(copia);
        return PerfilResponse.de(copia, 0);
    }

    /** Cria Administrador, Secretário e Coordenador para uma paróquia nova. */
    @Transactional
    public Perfil criarPadrao(UUID tenantId) {
        perfilRepository.save(PerfisPadrao.secretario(tenantId));
        perfilRepository.save(PerfisPadrao.coordenador(tenantId));
        return perfilRepository.save(PerfisPadrao.administrador(tenantId));
    }

    public Perfil administradorDe(UUID tenantId) {
        return perfilRepository.findByTenantIdAndNome(tenantId, PerfisPadrao.ADMINISTRADOR)
                .orElseGet(() -> criarPadrao(tenantId));
    }

    private void aplicar(Perfil perfil, PerfilRequest request) {
        // O ativo vem antes do retorno do acesso total: antes ficava depois
        // e inativar um perfil com "Liberar todas as opções" era ignorado.
        perfil.setAtivo(perfil.isSistema() || request.ativo());
        if (perfil.isSistema() || request.acessoTotal()) {
            perfil.setAcessoTotal(true);
            perfil.getPermissoes().clear();
            return;
        }
        perfil.setAcessoTotal(false);
        perfil.getPermissoes().clear();
        if (perfil.getId() != null) {
            perfilRepository.flush();
        }
        perfil.substituirPermissoes(normalizar(request.permissoes()));
    }

    /**
     * Só códigos do catálogo; ação marcada traz o módulo junto (a ação sem
     * o acesso ao módulo não teria como ser usada — mesma regra da tela).
     */
    private List<String> normalizar(List<String> pedidas) {
        if (pedidas == null) {
            return List.of();
        }
        LinkedHashSet<String> codigos = new LinkedHashSet<>();
        for (String pedida : pedidas) {
            String codigo = pedida == null ? "" : pedida.trim();
            if (!CatalogoPermissao.codigos().contains(codigo)) {
                throw new BadRequestException("Permissão desconhecida: " + codigo);
            }
            String modulo = CatalogoPermissao.moduloDe(codigo);
            if (modulo != null) {
                codigos.add(modulo);
            }
            codigos.add(codigo);
        }
        return new ArrayList<>(codigos);
    }

    private List<String> codigos(Perfil perfil) {
        return perfil.getPermissoes().stream().map(PerfilPermissao::getPermissao).toList();
    }

    private Perfil carregar(UUID id) {
        return perfilRepository.findByIdAndTenantId(id, TenantContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil não encontrado."));
    }

    private long usuarios(UUID perfilId) {
        return usuarioTenantRepository.countByPerfil_IdAndStatus(perfilId, UsuarioTenant.Status.ATIVO);
    }

    private void garantirAdministradorRestante(UUID tenantId, UUID perfilQueSai) {
        long restantes = usuarioTenantRepository
                .countByTenant_IdAndStatusAndPerfil_AcessoTotalTrue(tenantId, UsuarioTenant.Status.ATIVO);
        long neste = usuarios(perfilQueSai);
        if (restantes - neste < 1) {
            throw new ConflictException("A paróquia precisa de ao menos um usuário ativo com acesso total.");
        }
    }
}
