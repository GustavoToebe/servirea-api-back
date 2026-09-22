package br.com.servire.api.escala;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.escala.dto.EscalaEventoRequest;
import br.com.servire.api.escala.dto.EscalaRequest;
import br.com.servire.api.escala.dto.EscalaVagaRequest;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.voluntario.VoluntarioRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Regras de negócio de escalas (Fase 9, seção 46/47/109 do plano mestre;
 * controle de faltas — {@link #registrarPresenca} — somado na Fase 11,
 * seção 131.5 item 11).
 */
@Service
public class EscalaService {

    private final EscalaRepository escalaRepository;
    private final EscalaVagaRepository escalaVagaRepository;
    private final VoluntarioRepository voluntarioRepository;
    private final AuditLogService auditLogService;

    public EscalaService(EscalaRepository escalaRepository, EscalaVagaRepository escalaVagaRepository,
                          VoluntarioRepository voluntarioRepository, AuditLogService auditLogService) {
        this.escalaRepository = escalaRepository;
        this.escalaVagaRepository = escalaVagaRepository;
        this.voluntarioRepository = voluntarioRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<Escala> buscar(TipoEscala tipo, StatusEscala status, Integer ano, Integer mes) {
        return escalaRepository.buscar(tipo, status, ano, mes);
    }

    @Transactional(readOnly = true)
    public Escala buscarPorId(UUID id) {
        return escalaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escala não encontrada."));
    }

    @Transactional
    public Escala criar(EscalaRequest request, UUID usuarioIdCriador) {
        Escala escala = new Escala(request.titulo(), request.tipo());
        escala.setAno(request.ano());
        escala.setMes(request.mes());
        escala.setObservacao(request.observacao());
        escala.setCreatedBy(usuarioIdCriador);
        substituirEventos(escala, request.eventos());
        escala = escalaRepository.save(escala);
        auditLogService.registrar("CRIACAO", "ESCALA", escala.getId(), null);
        return escala;
    }

    /**
     * Controle otimista (seção 47): {@code request.version()} precisa
     * bater com a versão atual no banco, senão 409 — checagem explícita
     * ANTES de qualquer mudança, com a mensagem de negócio da seção 47
     * ("A escala foi alterada por outro usuário..."). O
     * {@code @Version} da entidade continua como rede de segurança contra
     * uma corrida de verdade entre duas requisições concorrentes que
     * passem por essa checagem quase ao mesmo tempo (ver
     * {@code GlobalExceptionHandler}).
     *
     * <p>Só permitida enquanto {@code RASCUNHO} — uma escala
     * {@code FINALIZADA} precisa ser reaberta primeiro
     * ({@link #reabrir}); uma {@code CANCELADA} não pode mais ser editada
     * (seção 46).</p>
     */
    @Transactional
    public Escala atualizar(UUID id, EscalaRequest request) {
        Escala escala = buscarPorId(id);
        if (escala.getStatus() != StatusEscala.RASCUNHO) {
            throw new ConflictException(
                    "Só é possível editar uma escala em RASCUNHO — reabra a escala antes de editar.");
        }
        if (request.version() == null || !request.version().equals(escala.getVersion())) {
            throw new ConflictException("A escala foi alterada por outro usuário. Atualize a página.");
        }
        escala.setTitulo(request.titulo());
        escala.setTipo(request.tipo());
        escala.setAno(request.ano());
        escala.setMes(request.mes());
        escala.setObservacao(request.observacao());
        substituirEventos(escala, request.eventos());
        auditLogService.registrar("ATUALIZACAO", "ESCALA", escala.getId(),
                List.of("titulo", "tipo", "ano", "mes", "observacao", "eventos"));
        return escala;
    }

    @Transactional
    public Escala finalizar(UUID id) {
        Escala escala = buscarPorId(id);
        if (escala.getStatus() != StatusEscala.RASCUNHO) {
            throw new ConflictException("Só é possível finalizar uma escala em RASCUNHO.");
        }
        escala.setStatus(StatusEscala.FINALIZADA);
        auditLogService.registrar("FINALIZACAO", "ESCALA", id, List.of("status"));
        return escala;
    }

    @Transactional
    public Escala cancelar(UUID id) {
        Escala escala = buscarPorId(id);
        if (escala.getStatus() == StatusEscala.CANCELADA) {
            throw new ConflictException("Esta escala já está cancelada.");
        }
        escala.setStatus(StatusEscala.CANCELADA);
        auditLogService.registrar("CANCELAMENTO", "ESCALA", id, List.of("status"));
        return escala;
    }

    /** Volta uma escala {@code FINALIZADA} (ou {@code CANCELADA}, para permitir corrigir um cancelamento por engano) para {@code RASCUNHO}, liberando edição de novo. */
    @Transactional
    public Escala reabrir(UUID id) {
        Escala escala = buscarPorId(id);
        if (escala.getStatus() == StatusEscala.RASCUNHO) {
            throw new ConflictException("Esta escala já está em RASCUNHO.");
        }
        escala.setStatus(StatusEscala.RASCUNHO);
        auditLogService.registrar("REABERTURA", "ESCALA", id, List.of("status"));
        return escala;
    }

    /** Só permite excluir de fato uma escala {@code CANCELADA} (seção 109 — regra explícita do plano mestre). */
    @Transactional
    public void excluir(UUID id) {
        Escala escala = buscarPorId(id);
        if (escala.getStatus() != StatusEscala.CANCELADA) {
            throw new ConflictException("Só é possível excluir uma escala CANCELADA.");
        }
        escalaRepository.delete(escala);
        auditLogService.registrar("EXCLUSAO", "ESCALA", id, null);
    }

    /**
     * Controle de faltas (Fase 11, seção 131.5 item 11): marca a presença
     * do voluntário alocado numa vaga específica, depois do evento
     * acontecer. Duas regras de negócio, ambas falhando alto e cedo:
     * não faz sentido marcar presença/falta numa vaga sem voluntário
     * alocado, nem numa escala já {@code CANCELADA} (o evento nem deveria
     * ter ocorrido de verdade).
     */
    @Transactional
    public EscalaVaga registrarPresenca(UUID vagaId, Presenca presenca) {
        EscalaVaga vaga = escalaVagaRepository.findById(vagaId)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        if (vaga.getVoluntario() == null) {
            throw new BadRequestException("Esta vaga não tem voluntário alocado — não há presença para registrar.");
        }
        if (vaga.getEvento().getEscala().getStatus() == StatusEscala.CANCELADA) {
            throw new ConflictException("Não é possível registrar presença numa escala CANCELADA.");
        }
        vaga.setPresenca(presenca);
        auditLogService.registrar("PRESENCA_REGISTRADA", "ESCALA_VAGA", vagaId, List.of("presenca"));
        return vaga;
    }

    /**
     * "Apaga tudo e reinsere" (ver javadoc de {@link EscalaEvento}) — o
     * {@code clear()} na coleção gerenciada, seguido de reinserção,
     * dispara DELETE dos eventos/vagas antigos ({@code orphanRemoval}) e
     * INSERT dos novos no flush, sem SQL nativo.
     */
    private void substituirEventos(Escala escala, List<EscalaEventoRequest> requests) {
        escala.getEventos().clear();
        for (EscalaEventoRequest er : requests) {
            EscalaEvento evento = new EscalaEvento(er.data(), er.horario(), er.celebracao());
            evento.setEscala(escala);
            Set<UUID> voluntariosNesteEvento = new HashSet<>();
            for (EscalaVagaRequest vr : er.vagas()) {
                EscalaVaga vaga = new EscalaVaga(vr.funcao(), vr.posicao());
                vaga.setEvento(evento);
                if (vr.voluntarioId() != null) {
                    if (!voluntariosNesteEvento.add(vr.voluntarioId())) {
                        throw new BadRequestException(
                                "O mesmo voluntário não pode ocupar duas vagas no mesmo evento (data " + er.data() + ").");
                    }
                    Voluntario voluntario = voluntarioRepository.findById(vr.voluntarioId())
                            .orElseThrow(() -> new BadRequestException("Voluntário informado na escala não encontrado."));
                    vaga.setVoluntario(voluntario);
                }
                evento.getVagas().add(vaga);
            }
            escala.getEventos().add(evento);
        }
    }
}
