package br.com.servire.api.comunicacao;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.comunicacao.dto.LayoutRequest;
import br.com.servire.api.comunicacao.dto.PreVisualizacaoResponse;
import br.com.servire.api.comunicacao.dto.PreVisualizarRequest;
import br.com.servire.api.comunicacao.dto.TagResponse;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CRUD de layouts de envio (PLANO-004).
 * Validações: nome único na paróquia (case-insensitive), tags conhecidas
 * para o tipo de layout, limites de caracteres (4096 WhatsApp / 50000 e-mail).
 */
@Service
public class LayoutService {

    private final LayoutRepository layoutRepository;
    private final AuditLogService auditLogService;
    private final TenantRepository tenantRepository;

    public LayoutService(LayoutRepository layoutRepository, AuditLogService auditLogService,
                         TenantRepository tenantRepository) {
        this.layoutRepository = layoutRepository;
        this.auditLogService = auditLogService;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public List<Layout> listar(TipoLayout tipoLayout, TipoEnvio tipoEnvio, Boolean ativo, String nome) {
        Specification<Layout> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tipoLayout != null) predicates.add(cb.equal(root.get("tipoLayout"), tipoLayout));
            if (tipoEnvio != null) predicates.add(cb.equal(root.get("tipoEnvio"), tipoEnvio));
            if (ativo != null) predicates.add(cb.equal(root.get("ativo"), ativo));
            if (nome != null && !nome.isBlank())
                predicates.add(cb.like(cb.lower(root.get("nome")), "%" + nome.toLowerCase() + "%"));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return layoutRepository.findAll(spec, Sort.by("nome"));
    }

    @Transactional(readOnly = true)
    public Layout buscarPorId(UUID id) {
        return layoutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Layout não encontrado."));
    }

    @Transactional
    public Layout criar(LayoutRequest req) {
        validarNome(req.nome(), null);
        validarConteudo(req.tipoEnvio(), req.tipoLayout(), req.conteudo());
        String assunto = req.tipoEnvio() == TipoEnvio.WHATSAPP ? null : req.assunto();
        Layout layout = new Layout(req.nome(), req.tipoLayout(), req.tipoEnvio(), assunto, req.conteudo(), req.ativo());
        Layout salvo = layoutRepository.save(layout);
        auditLogService.registrar("LAYOUT_CRIAR", "layout_envio", salvo.getId(), List.of("nome", "tipoLayout", "tipoEnvio", "conteudo"));
        return salvo;
    }

    @Transactional
    public Layout atualizar(UUID id, LayoutRequest req) {
        Layout layout = buscarPorId(id);
        validarNome(req.nome(), id);
        validarConteudo(req.tipoEnvio(), req.tipoLayout(), req.conteudo());
        layout.setNome(req.nome());
        layout.setTipoLayout(req.tipoLayout());
        layout.setTipoEnvio(req.tipoEnvio());
        layout.setAssunto(req.tipoEnvio() == TipoEnvio.WHATSAPP ? null : req.assunto());
        layout.setConteudo(req.conteudo());
        layout.setAtivo(req.ativo());
        layout.setUpdatedAt(Instant.now());
        Layout salvo = layoutRepository.save(layout);
        auditLogService.registrar("LAYOUT_ALTERAR", "layout_envio", salvo.getId(), List.of("nome", "tipoLayout", "tipoEnvio", "conteudo", "ativo"));
        return salvo;
    }

    @Transactional
    public void excluir(UUID id) {
        Layout layout = buscarPorId(id);
        layoutRepository.delete(layout);
        auditLogService.registrar("LAYOUT_EXCLUIR", "layout_envio", id, List.of());
    }

    public List<TagResponse> tags(TipoLayout tipoLayout) {
        return CatalogoDeTags.tagsDo(tipoLayout).stream()
                .map(t -> new TagResponse(t.codigo(), t.descricao()))
                .toList();
    }

    public PreVisualizacaoResponse preVisualizar(PreVisualizarRequest req) {
        UUID tenantId = TenantContext.get();
        String paroquiaNome = null;
        String paroquiaCidade = null;
        String paroquiaUf = null;
        if (tenantId != null) {
            var tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant != null) {
                paroquiaNome = tenant.getNome();
            }
        }
        ContextoDeEnvio ctx = ContextoDeEnvio.exemplo(paroquiaNome, paroquiaCidade, paroquiaUf);
        String assuntoRendered = req.assunto() != null
                ? Renderizador.renderizar(req.assunto(), req.tipoEnvio(), ctx)
                : null;
        String conteudoRendered = Renderizador.renderizar(
                req.conteudo() != null ? req.conteudo() : "", req.tipoEnvio(), ctx);
        return new PreVisualizacaoResponse(assuntoRendered, conteudoRendered);
    }

    private void validarNome(String nome, UUID idExistente) {
        boolean existe = idExistente == null
                ? layoutRepository.existsByNomeIgnoreCase(nome)
                : layoutRepository.existsByNomeIgnoreCaseAndIdNot(nome, idExistente);
        if (existe) {
            throw new ConflictException("Já existe um layout com este nome nesta paróquia.");
        }
    }

    private void validarConteudo(TipoEnvio tipoEnvio, TipoLayout tipoLayout, String conteudo) {
        if (tipoEnvio == TipoEnvio.WHATSAPP && conteudo != null && conteudo.length() > 4096) {
            throw new BadRequestException("Conteúdo WhatsApp não pode ter mais de 4096 caracteres.");
        }
        List<String> desconhecidas = Renderizador.tagsDesconhecidas(conteudo, tipoLayout);
        if (!desconhecidas.isEmpty()) {
            throw new BadRequestException(
                    "Tags que não existem para este tipo de layout: " + String.join(", ", desconhecidas));
        }
    }
}
