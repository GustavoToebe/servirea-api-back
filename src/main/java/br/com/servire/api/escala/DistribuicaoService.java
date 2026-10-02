package br.com.servire.api.escala;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.escala.MotorDistribuicao.Negativa;
import br.com.servire.api.escala.MotorDistribuicao.Positiva;
import br.com.servire.api.escala.dto.DistribuicaoDtos.*;
import br.com.servire.api.voluntario.*;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Prévia e aplicação da distribuição por regras (F04). A prévia só lê; a aplicação trava a escala,
 * confere a versão, refaz o cálculo com os dados de agora e só grava as escolhas que o motor ainda
 * produz. Nada preenche vaga já ocupada, linha de referência ou escala fora de rascunho.
 */
@Service
public class DistribuicaoService {
    static final int MAX_VAGAS = 600;

    @PersistenceContext
    private EntityManager em;
    private final EscalaRepository escalas;
    private final VoluntarioRepository voluntarios;
    private final DisponibilidadeVoluntarioRepository disponibilidades;
    private final IndisponibilidadeRepository indisponibilidades;
    private final RespostaIndisponibilidadeRepository respostas;
    private final AuditLogService auditLogService;

    public DistribuicaoService(EscalaRepository escalas, VoluntarioRepository voluntarios,
                               DisponibilidadeVoluntarioRepository disponibilidades,
                               IndisponibilidadeRepository indisponibilidades,
                               RespostaIndisponibilidadeRepository respostas, AuditLogService auditLogService) {
        this.escalas = escalas;
        this.voluntarios = voluntarios;
        this.disponibilidades = disponibilidades;
        this.indisponibilidades = indisponibilidades;
        this.respostas = respostas;
        this.auditLogService = auditLogService;
    }

    private record Contexto(Escala escala, Map<UUID, EscalaVaga> vagas, MotorDistribuicao.Resultado resultado,
                            Map<UUID, Voluntario> pessoas) {}

    @Transactional(readOnly = true)
    public Previa previa(UUID escalaId, Regras regras) {
        Escala escala = escalas.findById(escalaId)
                .orElseThrow(() -> new ResourceNotFoundException("Escala não encontrada."));
        return montar(calcular(escala, regras));
    }

    @Transactional
    public Aplicada aplicar(UUID escalaId, AplicarRequest req) {
        Escala escala = escalas.bloquear(escalaId)
                .orElseThrow(() -> new ResourceNotFoundException("Escala não encontrada."));
        if (!req.versao().equals(escala.getVersion())) {
            throw new ConflictException("A escala foi alterada depois da prévia. Gere a prévia novamente.");
        }
        Contexto ctx = calcular(escala, req.regras());
        if (ctx.resultado().bloqueado()) {
            throw new ConflictException("Há alocações existentes em conflito com as regras. Revise-as antes de aplicar.");
        }
        Map<UUID, UUID> aprovadas = ctx.resultado().sugestoes().stream()
                .collect(Collectors.toMap(MotorDistribuicao.Sugestao::vagaId, MotorDistribuicao.Sugestao::pessoaId));
        Set<UUID> vistas = new HashSet<>();
        for (Escolha e : req.escolhas()) {
            if (!vistas.add(e.vagaId())) {
                throw new BadRequestException("Vaga repetida na seleção.");
            }
            if (!e.pessoaId().equals(aprovadas.get(e.vagaId()))) {
                throw new ConflictException("A sugestão mudou depois da prévia. Gere a prévia novamente.");
            }
        }
        for (Escolha e : req.escolhas()) {
            EscalaVaga vaga = ctx.vagas().get(e.vagaId());
            vaga.setVoluntario(ctx.pessoas().get(e.pessoaId()));
            vaga.setPresenca(Presenca.PENDENTE);
        }
        em.lock(escala, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        em.flush();
        auditLogService.registrar("DISTRIBUICAO", "ESCALA", escalaId, List.of("vagas"));
        return new Aplicada(escalaId, escala.getVersion(), req.escolhas().size());
    }

    private Contexto calcular(Escala escala, Regras regras) {
        if (escala.getStatus() != StatusEscala.RASCUNHO) {
            throw new ConflictException("A distribuição só vale para escala em RASCUNHO.");
        }
        Map<UUID, EscalaVaga> porId = new LinkedHashMap<>();
        List<MotorDistribuicao.Vaga> vagas = new ArrayList<>();
        for (EscalaEvento ev : escala.getEventos()) {
            if (ev.isReferencia()) {
                continue;
            }
            for (EscalaVaga v : ev.getVagas()) {
                porId.put(v.getId(), v);
                vagas.add(new MotorDistribuicao.Vaga(v.getId(), ev.getId(), ev.getData(), ev.getHorario(), v.getFuncao(),
                        v.getVoluntario() == null ? null : v.getVoluntario().getId(), v.getRespostaVersao()));
            }
        }
        if (vagas.isEmpty()) {
            throw new BadRequestException("A escala não tem vagas para distribuir.");
        }
        if (vagas.size() > MAX_VAGAS) {
            throw new BadRequestException("A distribuição aceita até " + MAX_VAGAS + " vagas por vez.");
        }
        LocalDate min = vagas.stream().map(MotorDistribuicao.Vaga::data).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate max = vagas.stream().map(MotorDistribuicao.Vaga::data).max(Comparator.naturalOrder()).orElseThrow();
        LocalDate de = min.minusDays(regras.intervaloDias());
        LocalDate ate = max.plusDays(regras.intervaloDias());

        List<Voluntario> ativos = voluntarios.findByAtivoTrueOrderByPessoa_NomeCompletoAsc();
        List<UUID> ids = ativos.stream().map(Voluntario::getId).toList();
        Map<UUID, List<DisponibilidadeVoluntario>> disp = ids.isEmpty() ? Map.of()
                : disponibilidades.findByVoluntario_IdIn(ids).stream()
                        .collect(Collectors.groupingBy(d -> d.getVoluntario().getId()));
        Map<UUID, List<Negativa>> negativas = indisponibilidades
                .findByDataBetweenOrderByDataAsc(min.withDayOfMonth(1), max.withDayOfMonth(max.lengthOfMonth())).stream()
                .collect(Collectors.groupingBy(Indisponibilidade::getVoluntarioId,
                        Collectors.mapping(i -> new Negativa(i.getData(), i.getPeriodo()), Collectors.toList())));
        Map<UUID, Set<YearMonth>> respondidos = new HashMap<>();
        for (YearMonth m = YearMonth.from(min); !m.isAfter(YearMonth.from(max)); m = m.plusMonths(1)) {
            for (RespostaIndisponibilidade r : respostas.findByAnoAndMes(m.getYear(), m.getMonthValue())) {
                if (r.isSemRestricao()) {
                    respondidos.computeIfAbsent(r.getVoluntarioId(), k -> new HashSet<>()).add(m);
                }
            }
        }
        List<MotorDistribuicao.Pessoa> pessoas = new ArrayList<>();
        Map<UUID, Voluntario> mapa = new HashMap<>();
        for (Voluntario v : ativos) {
            mapa.put(v.getId(), v);
            FuncaoEscala[] habilitadas = v.getFuncoesHabilitadas();
            Set<FuncaoEscala> funcoes = habilitadas == null || habilitadas.length == 0
                    ? Set.of() : EnumSet.copyOf(Arrays.asList(habilitadas));
            List<Positiva> positivas = disp.getOrDefault(v.getId(), List.of()).stream()
                    .map(d -> new Positiva(d.getData(), d.getDiaSemana(), d.getPeriodo())).toList();
            pessoas.add(new MotorDistribuicao.Pessoa(v.getId(), v.getPessoa().getNomeCompleto(), funcoes, positivas,
                    negativas.getOrDefault(v.getId(), List.of()), respondidos.getOrDefault(v.getId(), Set.of())));
        }
        List<MotorDistribuicao.Reserva> outras = em.createQuery(
                        "select v from EscalaVaga v join fetch v.evento e where e.escala.id <> :id "
                                + "and e.escala.status <> :cancelada and e.referencia = false "
                                + "and v.voluntario is not null and e.data between :de and :ate", EscalaVaga.class)
                .setParameter("id", escala.getId()).setParameter("cancelada", StatusEscala.CANCELADA)
                .setParameter("de", de).setParameter("ate", ate).getResultList().stream()
                .map(v -> new MotorDistribuicao.Reserva(v.getVoluntario().getId(), v.getEvento().getId(),
                        v.getEvento().getData(), v.getEvento().getHorario()))
                .toList();
        var resultado = MotorDistribuicao.gerar(vagas, pessoas, outras,
                new MotorDistribuicao.Regras(regras.maximoPorPessoa(), regras.intervaloDias(), regras.exigirResposta()));
        return new Contexto(escala, porId, resultado, mapa);
    }

    private Previa montar(Contexto ctx) {
        var sugestoes = ctx.resultado().sugestoes().stream().map(s -> {
            EscalaVaga v = ctx.vagas().get(s.vagaId());
            return new Sugestao(v.getId(), v.getEvento().getId(), v.getEvento().getData(), v.getEvento().getHorario(),
                    v.getEvento().getCelebracao(), v.getFuncao(), s.pessoaId(), s.nome(), s.explicacao());
        }).toList();
        var conflitos = ctx.resultado().conflitos().stream().map(c -> {
            EscalaVaga v = ctx.vagas().get(c.vagaId());
            var descartes = c.descartes().entrySet().stream()
                    .map(e -> new Descarte(e.getKey(), MotorDistribuicao.rotulo(e.getKey()), e.getValue())).toList();
            return new Conflito(v.getId(), v.getEvento().getId(), v.getEvento().getData(), v.getEvento().getHorario(),
                    v.getEvento().getCelebracao(), v.getFuncao(), c.explicacao(), descartes, c.alocacaoExistente());
        }).toList();
        int vazias = (int) ctx.vagas().values().stream().filter(v -> v.getVoluntario() == null).count();
        return new Previa(ctx.escala().getId(), ctx.escala().getVersion(), vazias, sugestoes, conflitos,
                ctx.resultado().bloqueado());
    }
}
