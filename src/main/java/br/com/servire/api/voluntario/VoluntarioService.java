package br.com.servire.api.voluntario;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.escala.EscalaVagaRepository;
import br.com.servire.api.storage.ExtensaoDeFoto;
import br.com.servire.api.storage.StorageService;
import br.com.servire.api.voluntario.dto.CompromissoResponse;
import br.com.servire.api.voluntario.dto.ContagemVoluntarios;
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
    private final br.com.servire.api.minhaconta.CotasService cotas;

    public VoluntarioService(VoluntarioRepository voluntarioRepository,
                              EscalaVagaRepository escalaVagaRepository,
                              StorageService storageService,
                              AuditLogService auditLogService, br.com.servire.api.minhaconta.CotasService cotas) {
        this.voluntarioRepository = voluntarioRepository;
        this.escalaVagaRepository = escalaVagaRepository;
        this.storageService = storageService;
        this.auditLogService = auditLogService; this.cotas = cotas;
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
    public br.com.servire.api.voluntario.dto.PainelVoluntarios painel() {
        long ativos = 0, coroinhas = 0, acolitos = 0, mesc = 0;
        for (Object[] linha : voluntarioRepository.contarAtivosPorTipo()) {
            TipoVoluntario tipo = (TipoVoluntario) linha[0];
            long n = (Long) linha[1];
            ativos += n;
            if (tipo == TipoVoluntario.COROINHA || tipo == TipoVoluntario.AMBOS) coroinhas += n;
            if (tipo == TipoVoluntario.ACOLITO || tipo == TipoVoluntario.AMBOS) acolitos += n;
            if (tipo == TipoVoluntario.MESC) mesc += n;
        }
        java.time.LocalDate limite = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(90);
        return new br.com.servire.api.voluntario.dto.PainelVoluntarios(ativos, coroinhas, acolitos, mesc, voluntarioRepository.contarMandatosAte(limite));
    }

    @Transactional(readOnly = true)
    public ContagemVoluntarios contarAtivosEInativos() {
        long ativos = 0;
        long inativos = 0;
        for (Object[] linha : voluntarioRepository.contarAgrupadoPorAtivo()) {
            boolean ativo = Boolean.TRUE.equals(linha[0]);
            long quantidade = (Long) linha[1];
            if (ativo) {
                ativos = quantidade;
            } else {
                inativos = quantidade;
            }
        }
        return new ContagemVoluntarios(ativos, inativos);
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

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.servire.api.storage.ArquivoCicloService arquivos;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.transaction.PlatformTransactionManager transacoes;

    /**
     * Troca a foto em três passos curtos: (1) transação que confere a cota e trava a paróquia só pelo tempo da conferência,
     * (2) upload fora de qualquer transação, com o UPLOAD registrado de forma durável antes, (3) transação que revalida a cota,
     * grava a referência e agenda a remoção da foto anterior. Falha depois do upload deixa uma pendência que o job limpa.
     */
    public Voluntario definirFoto(UUID id, MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            throw new BadRequestException("Nenhum arquivo de foto enviado.");
        }
        byte[] conteudo;
        try {
            conteudo = foto.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo de foto enviado.", e);
        }
        String tipo = foto.getContentType();
        String caminho = id + "/perfil-" + UUID.randomUUID() + ExtensaoDeFoto.de(tipo);
        var curta = new org.springframework.transaction.support.TransactionTemplate(transacoes);
        curta.executeWithoutResult(tx -> {
            var reserva = cotas.reservar();
            cotas.validarUpload(reserva, conteudo.length, buscarPorId(id).getFotoPath());
        });
        arquivos.registrarUpload(caminho);
        String retornado = storageService.armazenar(caminho, conteudo, tipo);
        String salvo = retornado != null ? retornado : caminho;
        try {
            return curta.execute(tx -> {
                var reserva = cotas.reservar();
                Voluntario voluntario = buscarPorId(id);
                String anterior = voluntario.getFotoPath();
                cotas.validarUpload(reserva, conteudo.length, anterior);
                voluntario.setFotoPath(salvo);
                voluntario.setFotoTamanhoBytes((long) conteudo.length);
                cotas.validar(reserva);
                auditLogService.registrar("FOTO_ATUALIZADA", "VOLUNTARIO", id, List.of("fotoPath"));
                if (anterior != null && !anterior.equals(salvo)) {
                    arquivos.agendarRemocao(anterior);
                }
                arquivos.aoConfirmar(caminho);
                return voluntario;
            });
        } catch (RuntimeException e) {
            arquivos.descartarUpload(caminho);
            throw e;
        }
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

}
