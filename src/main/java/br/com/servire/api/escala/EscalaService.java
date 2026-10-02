package br.com.servire.api.escala;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.escala.dto.CandidatoResponse;
import br.com.servire.api.escala.dto.EscalaEventoRequest;
import br.com.servire.api.escala.dto.EscalaRequest;
import br.com.servire.api.escala.dto.EscalaVagaRequest;
import br.com.servire.api.voluntario.DisponibilidadeVoluntario;
import br.com.servire.api.voluntario.DisponibilidadeVoluntarioRepository;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.Periodo;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.voluntario.VoluntarioRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.Hibernate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Regras de negócio de escalas (Fase 9, seção 46/47/109 do plano mestre;
 * controle de faltas — {@link #registrarPresenca} — somado na Fase 11,
 * seção 131.5 item 11; picker de candidatos — {@link #listarCandidatos}
 * — seção 49; alocação pontual — {@link #alocarVaga}).
 */
@Service
public class EscalaService {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private final EscalaRepository escalaRepository;
    private final EscalaVagaRepository escalaVagaRepository;
    private final EscalaEventoRepository escalaEventoRepository;
    private final VoluntarioRepository voluntarioRepository;
    private final DisponibilidadeVoluntarioRepository disponibilidadeRepository;
    private final LayoutEscalaRepository layoutEscalaRepository;
    private final AuditLogService auditLogService;

    static final String REFERENCIA_SEM_ALOCACAO = "Linha de referência não recebe presença nem alocação.";

    public EscalaService(EscalaRepository escalaRepository, EscalaVagaRepository escalaVagaRepository,
                          EscalaEventoRepository escalaEventoRepository,
                          VoluntarioRepository voluntarioRepository,
                          DisponibilidadeVoluntarioRepository disponibilidadeRepository,
                          LayoutEscalaRepository layoutEscalaRepository,
                          AuditLogService auditLogService) {
        this.escalaRepository = escalaRepository;
        this.escalaVagaRepository = escalaVagaRepository;
        this.escalaEventoRepository = escalaEventoRepository;
        this.voluntarioRepository = voluntarioRepository;
        this.disponibilidadeRepository = disponibilidadeRepository;
        this.layoutEscalaRepository = layoutEscalaRepository;
        this.auditLogService = auditLogService;
    }

    /** Mais recente primeiro; escala sem ano/mês vai para o fim. */
    private static final Comparator<Escala> ORDEM_DA_LISTA = Comparator
            .comparing(Escala::getAno, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Escala::getMes, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Escala::getTitulo, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * Filtros opcionais via {@link Specification} — a JPQL anterior
     * ({@code :tipo IS NULL OR ...}) é a armadilha conhecida do Hibernate 7
     * + Postgres. Cada escala volta com eventos e vagas inicializados
     * ({@link #inicializar}); o {@code default_batch_fetch_size} evita um
     * SELECT por escala.
     */
    @Transactional(readOnly = true)
    public List<Escala> buscar(TipoEscala tipo, StatusEscala status, Integer ano, Integer mes) {
        List<Escala> escalas = new ArrayList<>(escalaRepository.findAll(filtro(tipo, status, ano, mes)));
        escalas.sort(ORDEM_DA_LISTA);
        escalas.forEach(EscalaService::inicializar);
        return escalas;
    }

    private static Specification<Escala> filtro(TipoEscala tipo, StatusEscala status, Integer ano, Integer mes) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (tipo != null) {
                predicados.add(cb.equal(root.get("tipo"), tipo));
            }
            if (status != null) {
                predicados.add(cb.equal(root.get("status"), status));
            }
            if (ano != null) {
                predicados.add(cb.equal(root.get("ano"), ano));
            }
            if (mes != null) {
                predicados.add(cb.equal(root.get("mes"), mes));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    /** Escala pronta para {@code EscalaResponse} ({@code open-in-view: false}). */
    @Transactional(readOnly = true)
    public Escala buscarPorId(UUID id) {
        return inicializar(carregar(id));
    }

    private void invalidarCandidaturas(UUID escalaId) {
        entityManager.createQuery("update Candidatura c set c.situacao=:expirada,c.atualizadaEm=:agora,c.versao=c.versao+1 where c.escalaId=:escala and c.situacao=:pendente")
                .setParameter("expirada", br.com.servire.api.portal.Candidatura.Situacao.EXPIRADA)
                .setParameter("pendente", br.com.servire.api.portal.Candidatura.Situacao.PENDENTE)
                .setParameter("agora", java.time.Instant.now()).setParameter("escala", escalaId).executeUpdate();
    }

    private Escala carregarParaAlterar(UUID id) {
        return escalaRepository.bloquear(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escala não encontrada."));
    }

    private Escala carregar(UUID id) {
        return escalaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escala não encontrada."));
    }

    /**
     * Toca eventos → vagas → voluntário → nome ainda dentro da transação.
     * Sem isto o {@code EscalaResponse.de} no controller estourava
     * {@code LazyInitializationException} (bug de 24/09/2026 — a API de
     * escalas só tinha teste de service, nunca tinha sido chamada via HTTP).
     */
    private static Escala inicializar(Escala escala) {
        for (EscalaEvento evento : escala.getEventos()) {
            evento.getVagas().forEach(EscalaService::inicializar);
        }
        return escala;
    }

    private static EscalaVaga inicializar(EscalaVaga vaga) {
        if (vaga.getVoluntario() != null) {
            Hibernate.initialize(vaga.getVoluntario().getPessoa());
        }
        return vaga;
    }

    /**
     * Picker de candidatos (seção 49): tenant atual (via {@code @TenantId}),
     * ativo, função habilitada, ainda não alocado neste evento, e
     * disponibilidade compatível com data/horário do evento.
     *
     * <p>Sem nenhuma disponibilidade cadastrada o voluntário entra — o
     * cadastro de disponibilidade é opt-in; esconder quem ainda não
     * preencheu esvaziaria o picker nas paróquias existentes. Quem
     * cadastrou alguma linha precisa bater dia (recorrente ou pontual) e
     * período ({@code 00:00–11:59} manhã, {@code 12:00–17:59} tarde,
     * {@code 18:00+} noite).</p>
     *
     * <p>Não há matriz {@code tipo × função} no plano (tipo é
     * COROINHA/ACOLITO/AMBOS; função é MISSAL/CRUZ/…); o tipo vai só na
     * resposta para o front filtrar se quiser.</p>
     */
    @Transactional(readOnly = true)
    public List<CandidatoResponse> listarCandidatos(UUID eventoId, FuncaoEscala funcao) {
        EscalaEvento evento = escalaEventoRepository.findById(eventoId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        if (evento.isReferencia()) {
            return List.of();
        }
        Set<UUID> jaAlocados = evento.getVagas().stream()
                .map(EscalaVaga::getVoluntario)
                .filter(v -> v != null)
                .map(Voluntario::getId)
                .collect(Collectors.toSet());
        List<Voluntario> ativos = voluntarioRepository.findByAtivoTrueOrderByPessoa_NomeCompletoAsc();
        List<UUID> idsAtivos = ativos.stream().map(Voluntario::getId).toList();
        Map<UUID, List<DisponibilidadeVoluntario>> porVoluntario = idsAtivos.isEmpty()
                ? Map.of()
                : disponibilidadeRepository.findByVoluntario_IdIn(idsAtivos).stream()
                        .collect(Collectors.groupingBy(d -> d.getVoluntario().getId()));
        Periodo periodo = periodoDe(evento.getHorario());
        DayOfWeek diaSemana = evento.getData().getDayOfWeek();
        LocalDate data = evento.getData();
        return ativos.stream()
                .filter(v -> !jaAlocados.contains(v.getId()))
                .filter(v -> temFuncao(v, funcao))
                .filter(v -> disponivelEm(porVoluntario.getOrDefault(v.getId(), List.of()), data, diaSemana, periodo))
                .map(CandidatoResponse::de)
                .toList();
    }

    static Periodo periodoDe(LocalTime horario) {
        if (horario.isBefore(LocalTime.NOON)) {
            return Periodo.MANHA;
        }
        if (horario.isBefore(LocalTime.of(18, 0))) {
            return Periodo.TARDE;
        }
        return Periodo.NOITE;
    }

    private static boolean temFuncao(Voluntario voluntario, FuncaoEscala funcao) {
        FuncaoEscala[] funcoes = voluntario.getFuncoesHabilitadas();
        if (funcoes == null) {
            return false;
        }
        for (FuncaoEscala habilitada : funcoes) {
            if (habilitada == funcao) {
                return true;
            }
        }
        return false;
    }

    private static boolean disponivelEm(List<DisponibilidadeVoluntario> disponibilidades,
                                        LocalDate data, DayOfWeek diaSemana, Periodo periodo) {
        if (disponibilidades.isEmpty()) {
            return true;
        }
        return disponibilidades.stream().anyMatch(d ->
                d.getPeriodo() == periodo
                        && ((d.getData() != null && d.getData().equals(data))
                        || (d.getDiaSemana() != null && d.getDiaSemana() == diaSemana)));
    }

    @Transactional
    public Escala criar(EscalaRequest request, UUID usuarioIdCriador) {
        Escala escala = new Escala(request.titulo(), request.tipo());
        escala.setAno(request.ano());
        escala.setMes(request.mes());
        escala.setObservacao(request.observacao());
        escala.setCreatedBy(usuarioIdCriador);
        
        preencherLayoutEColunas(escala, request);

        substituirEventos(escala, request.eventos());
        escala = escalaRepository.save(escala);
        auditLogService.registrar("CRIACAO", "ESCALA", escala.getId(), null);
        return inicializar(escala);
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
        Escala escala = carregarParaAlterar(id);
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
        
        preencherLayoutEColunas(escala, request);

        substituirEventos(escala, request.eventos());
        auditLogService.registrar("ATUALIZACAO", "ESCALA", escala.getId(),
                List.of("titulo", "tipo", "ano", "mes", "observacao", "eventos", "layoutId", "colunas"));
        return inicializar(escala);
    }

    private void preencherLayoutEColunas(Escala escala, EscalaRequest request) {
        if (request.colunas() != null && !request.colunas().isEmpty()) {
            escala.setColunas(request.colunas());
            escala.setLayoutId(request.layoutId());
        } else if (request.layoutId() != null) {
            LayoutEscala layout = layoutEscalaRepository.findById(request.layoutId())
                    .orElseThrow(() -> new ResourceNotFoundException("Layout não encontrado."));
            escala.setColunas(layout.getColunas());
            escala.setLayoutId(layout.getId());
        } else {
            LayoutEscala layout = layoutEscalaRepository.findFirstByTipoAndPadraoTrueAndAtivoTrue(escala.getTipo())
                    .or(() -> layoutEscalaRepository.findFirstByTipoAndSistemaTrueAndAtivoTrue(escala.getTipo()))
                    .orElse(null);
            if (layout != null) {
                escala.setColunas(layout.getColunas());
                escala.setLayoutId(layout.getId());
            } else {
                escala.setColunas(java.util.List.of());
            }
        }
    }

    @Transactional
    public Escala finalizar(UUID id) {
        Escala escala = carregarParaAlterar(id);
        if (escala.getStatus() != StatusEscala.RASCUNHO) {
            throw new ConflictException("Só é possível finalizar uma escala em RASCUNHO.");
        }
        if (escala.getEventos().stream().anyMatch(EscalaEvento::isReferencia)) {
            throw new BadRequestException("Apague as linhas de referência antes de finalizar a escala.");
        }
        escala.setStatus(StatusEscala.FINALIZADA);
        auditLogService.registrar("FINALIZACAO", "ESCALA", id, List.of("status"));
        return inicializar(escala);
    }

    @Transactional
    public Escala cancelar(UUID id) {
        Escala escala = carregarParaAlterar(id);
        if (escala.getStatus() == StatusEscala.CANCELADA) {
            throw new ConflictException("Esta escala já está cancelada.");
        }
        escala.setStatus(StatusEscala.CANCELADA);
        invalidarCandidaturas(id);
        auditLogService.registrar("CANCELAMENTO", "ESCALA", id, List.of("status"));
        return inicializar(escala);
    }

    /** Volta uma escala {@code FINALIZADA} (ou {@code CANCELADA}, para permitir corrigir um cancelamento por engano) para {@code RASCUNHO}, liberando edição de novo. */
    @Transactional
    public Escala reabrir(UUID id) {
        Escala escala = carregarParaAlterar(id);
        if (escala.getStatus() == StatusEscala.RASCUNHO) {
            throw new ConflictException("Esta escala já está em RASCUNHO.");
        }
        escala.setStatus(StatusEscala.RASCUNHO);
        invalidarCandidaturas(id);
        escala.getEventos().forEach(e -> e.getVagas().forEach(v -> {
            v.invalidarResposta();
            // Mesmo vaga vazia/PENDENTE começa um novo ciclo de candidatura ao reabrir.
            entityManager.lock(v, jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }));
        auditLogService.registrar("REABERTURA", "ESCALA", id, List.of("status"));
        return inicializar(escala);
    }

    /** Só permite excluir de fato uma escala {@code CANCELADA} (seção 109 — regra explícita do plano mestre). */
    @Transactional
    public void excluir(UUID id) {
        Escala escala = carregarParaAlterar(id);
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
        if (vaga.getEvento().isReferencia()) {
            throw new BadRequestException(REFERENCIA_SEM_ALOCACAO);
        }
        if (vaga.getVoluntario() == null) {
            throw new BadRequestException("Esta vaga não tem voluntário alocado — não há presença para registrar.");
        }
        if (vaga.getEvento().getEscala().getStatus() == StatusEscala.CANCELADA) {
            throw new ConflictException("Não é possível registrar presença numa escala CANCELADA.");
        }
        vaga.setPresenca(presenca);
        auditLogService.registrar("PRESENCA_REGISTRADA", "ESCALA_VAGA", vagaId, List.of("presenca"));
        return inicializar(vaga);
    }

    /**
     * Aloca (ou desaloca) um voluntário numa vaga, sem reenviar a escala
     * inteira. Complemento do picker (seção 49): o front escolhe um
     * candidato e grava só esta vaga.
     *
     * <p>Só em {@code RASCUNHO} (mesma regra do {@link #atualizar}).
     * Voluntário inativo ou já alocado em outra vaga do mesmo evento
     * falha alto. Trocar ou esvaziar zera a presença para
     * {@code PENDENTE} — não faz sentido manter PRESENTE/FALTOU de outra
     * pessoa.</p>
     */
    @Transactional
    public EscalaVaga alocarVaga(UUID vagaId, UUID voluntarioId) {
        return inicializar(alocar(vagaId, voluntarioId));
    }

    private EscalaVaga alocar(UUID vagaId, UUID voluntarioId) {
        UUID escalaId = entityManager.createQuery("select v.evento.escala.id from EscalaVaga v where v.id=:id", UUID.class)
                .setParameter("id", vagaId).getResultStream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        carregarParaAlterar(escalaId);
        EscalaVaga vaga = escalaVagaRepository.findById(vagaId)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        if (vaga.getEvento().isReferencia()) {
            throw new BadRequestException(REFERENCIA_SEM_ALOCACAO);
        }
        if (vaga.getEvento().getEscala().getStatus() != StatusEscala.RASCUNHO) {
            throw new ConflictException(
                    "Só é possível alocar voluntário numa escala em RASCUNHO — reabra a escala antes de editar.");
        }
        UUID atual = vaga.getVoluntario() != null ? vaga.getVoluntario().getId() : null;
        if (voluntarioId == null) {
            if (atual == null) {
                return vaga;
            }
            vaga.setVoluntario(null);
            vaga.setPresenca(Presenca.PENDENTE);
            auditLogService.registrar("ALOCACAO", "ESCALA_VAGA", vagaId, List.of("voluntario", "presenca"));
            return vaga;
        }
        if (voluntarioId.equals(atual)) {
            return vaga;
        }
        Voluntario voluntario = voluntarioRepository.findById(voluntarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Voluntário não encontrado."));
        if (!voluntario.isAtivo()) {
            throw new BadRequestException("Não é possível alocar um voluntário inativo.");
        }
        for (EscalaVaga outra : vaga.getEvento().getVagas()) {
            if (!outra.getId().equals(vaga.getId())
                    && outra.getVoluntario() != null
                    && voluntarioId.equals(outra.getVoluntario().getId())) {
                throw new BadRequestException(
                        "O mesmo voluntário não pode ocupar duas vagas no mesmo evento (data "
                                + vaga.getEvento().getData() + ").");
            }
        }
        vaga.setVoluntario(voluntario);
        vaga.setPresenca(Presenca.PENDENTE);
        auditLogService.registrar("ALOCACAO", "ESCALA_VAGA", vagaId, List.of("voluntario", "presenca"));
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
            evento.setReferencia(Boolean.TRUE.equals(er.referencia()));
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
