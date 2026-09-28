package br.com.servire.api.voluntario;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.escala.EscalaVagaRepository;
import br.com.servire.api.storage.StorageService;
import br.com.servire.api.voluntario.dto.CompromissoResponse;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Perfil de serviço do voluntário (lista, ativo, foto, compromissos).
 * Identidade, contato e relação passam por {@code PessoaService}.
 */
@Service
public class VoluntarioService {

    private final VoluntarioRepository voluntarioRepository;
    private final EscalaVagaRepository escalaVagaRepository;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    public VoluntarioService(VoluntarioRepository voluntarioRepository,
                              EscalaVagaRepository escalaVagaRepository,
                              StorageService storageService,
                              AuditLogService auditLogService) {
        this.voluntarioRepository = voluntarioRepository;
        this.escalaVagaRepository = escalaVagaRepository;
        this.storageService = storageService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<Voluntario> buscar(Boolean ativo, TipoVoluntario tipo, String nome) {
        return voluntarioRepository.findAll(filtroBusca(ativo, tipo, nome),
                Sort.by(Sort.Direction.ASC, "pessoa.nomeCompleto"));
    }

    private static Specification<Voluntario> filtroBusca(Boolean ativo, TipoVoluntario tipo, String nome) {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("pessoa", JoinType.INNER);
                query.distinct(true);
            }
            List<Predicate> predicados = new ArrayList<>();
            if (ativo != null) {
                predicados.add(cb.equal(root.get("ativo"), ativo));
            }
            if (tipo != null) {
                predicados.add(cb.equal(root.get("tipo"), tipo));
            }
            if (nome != null && !nome.isBlank()) {
                predicados.add(cb.like(cb.lower(root.get("pessoa").get("nomeCompleto")),
                        "%" + nome.trim().toLowerCase() + "%"));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public long contarPorAtivo(boolean ativo) {
        return voluntarioRepository.countByAtivo(ativo);
    }

    @Transactional(readOnly = true)
    public Voluntario buscarPorId(UUID id) {
        return voluntarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voluntário não encontrado."));
    }

    @Transactional
    public Voluntario setAtivo(UUID id, boolean ativo) {
        Voluntario voluntario = buscarPorId(id);
        voluntario.setAtivo(ativo);
        voluntarioRepository.save(voluntario);
        auditLogService.registrar(ativo ? "ATIVACAO" : "DESATIVACAO", "VOLUNTARIO", id, List.of("ativo"));
        return voluntario;
    }

    @Transactional
    public Voluntario definirFoto(UUID id, MultipartFile foto) {
        Voluntario voluntario = buscarPorId(id);
        if (foto == null || foto.isEmpty()) {
            throw new BadRequestException("Nenhum arquivo de foto enviado.");
        }
        String caminho = voluntario.getId() + "/perfil-" + System.currentTimeMillis() + extensaoDe(foto);
        byte[] conteudo;
        try {
            conteudo = foto.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo de foto enviado.", e);
        }
        String caminhoSalvo = storageService.armazenar(caminho, conteudo, foto.getContentType());
        voluntario.setFotoPath(caminhoSalvo);
        auditLogService.registrar("FOTO_ATUALIZADA", "VOLUNTARIO", id, List.of("fotoPath"));
        return voluntario;
    }

    @Transactional(readOnly = true)
    public List<CompromissoResponse> listarCompromissos(UUID id) {
        buscarPorId(id);
        return escalaVagaRepository.findByVoluntario_IdAndEvento_ReferenciaFalseOrderByEvento_DataAscEvento_HorarioAsc(id).stream()
                .map(CompromissoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public String obterUrlFoto(UUID id) {
        Voluntario voluntario = buscarPorId(id);
        if (voluntario.getFotoPath() == null) {
            throw new ResourceNotFoundException("Voluntário não possui foto cadastrada.");
        }
        return storageService.gerarUrlAssinada(voluntario.getFotoPath());
    }

    private String extensaoDe(MultipartFile foto) {
        String nomeOriginal = foto.getOriginalFilename();
        if (nomeOriginal != null && nomeOriginal.contains(".")) {
            return nomeOriginal.substring(nomeOriginal.lastIndexOf('.'));
        }
        return switch (String.valueOf(foto.getContentType())) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic" -> ".heic";
            default -> ".jpg";
        };
    }
}
