package br.com.servire.api.escala;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.ApoioEscala;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.ApoioVoluntario;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.Indisponivel;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.Item;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.MesRequest;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.MesResponse;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.Situacao;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.dto.ParDeIrmaos;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.voluntario.VoluntarioRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Indisponibilidades do mês (PLANO-007): a equipe digita quem NÃO pode em cada data (negativa, por data e
 * período) e quem respondeu "sem restrição". Quem não respondeu fica PENDENTE, nunca "disponível". Diferente
 * de {@code DisponibilidadeVoluntario} (V025, positiva). Irmãos = responsável em comum em {@code PessoaRelacao}.
 */
@Service
public class IndisponibilidadeService {

    private final IndisponibilidadeRepository indisponibilidades;
    private final RespostaIndisponibilidadeRepository respostas;
    private final VoluntarioRepository voluntarios;
    private final PessoaRepository pessoas;
    private final EscalaRepository escalas;
    private final AuditLogService auditLogService;

    @PersistenceContext
    private EntityManager entityManager;

    public IndisponibilidadeService(IndisponibilidadeRepository indisponibilidades, RespostaIndisponibilidadeRepository respostas,
                                    VoluntarioRepository voluntarios, PessoaRepository pessoas, EscalaRepository escalas,
                                    AuditLogService auditLogService) {
        this.indisponibilidades = indisponibilidades;
        this.respostas = respostas;
        this.voluntarios = voluntarios;
        this.pessoas = pessoas;
        this.escalas = escalas;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public MesResponse buscar(int ano, int mes) {
        YearMonth ym = mesValido(ano, mes);
        List<Item> itens = indisponibilidades.findByDataBetweenOrderByDataAsc(ym.atDay(1), ym.atEndOfMonth()).stream()
                .map(i -> new Item(i.getVoluntarioId(), i.getData(), i.getPeriodo(), i.getObservacao())).toList();
        List<UUID> sem = respostas.findByAnoAndMes(ano, mes).stream()
                .filter(RespostaIndisponibilidade::isSemRestricao).map(RespostaIndisponibilidade::getVoluntarioId).toList();
        return new MesResponse(ano, mes, itens, sem);
    }

    /** Substitui o mês inteiro. Apaga, faz {@code flush} (índice único parcial) e reinsere. */
    @Transactional
    public MesResponse salvar(int ano, int mes, MesRequest req) {
        YearMonth ym = mesValido(ano, mes);
        Set<UUID> comRestricao = new HashSet<>();
        Set<String> vistas = new HashSet<>();
        for (Item i : req.itens()) {
            if (!YearMonth.from(i.data()).equals(ym)) {
                throw new BadRequestException("A data " + i.data() + " não é do mês " + String.format("%02d/%d", mes, ano) + ".");
            }
            if (!vistas.add(i.voluntarioId() + "|" + i.data() + "|" + i.periodo())) {
                throw new BadRequestException("Indisponibilidade repetida para a mesma pessoa, data e período.");
            }
            comRestricao.add(i.voluntarioId());
        }
        Set<UUID> sem = new LinkedHashSet<>(req.semRestricao());
        if (sem.stream().anyMatch(comRestricao::contains)) {
            throw new BadRequestException("Quem tem restrição não pode estar como sem restrição.");
        }
        Set<UUID> todos = new HashSet<>(comRestricao);
        todos.addAll(sem);
        Set<UUID> daParoquia = voluntarios.findAllById(todos).stream().map(Voluntario::getId).collect(Collectors.toSet());
        if (!daParoquia.containsAll(todos)) {
            throw new BadRequestException("Há voluntário que não é desta paróquia.");
        }

        indisponibilidades.deleteByDataBetween(ym.atDay(1), ym.atEndOfMonth());
        respostas.deleteByAnoAndMes(ano, mes);
        entityManager.flush();
        indisponibilidades.saveAll(req.itens().stream()
                .map(i -> new Indisponibilidade(i.voluntarioId(), i.data(), i.periodo(), blankParaNull(i.observacao()))).toList());
        respostas.saveAll(sem.stream().map(v -> new RespostaIndisponibilidade(v, ano, mes, true)).toList());
        // A entidade é o mês da paróquia inteira: o id registrado é o da paróquia.
        auditLogService.registrar("ALTERACAO", "INDISPONIBILIDADE", br.com.servire.api.tenant.TenantContext.get(), List.of(ano + "/" + mes));
        return buscar(ano, mes);
    }

    /** Apoio à montagem da escala (mês da escala): situação, datas indisponíveis e irmãos de cada voluntário ativo. */
    @Transactional(readOnly = true)
    public ApoioEscala apoio(UUID escalaId) {
        Escala escala = escalas.findById(escalaId).orElseThrow(() -> new ResourceNotFoundException("Escala não encontrada."));
        YearMonth ym = YearMonth.of(escala.getAno(), escala.getMes());
        List<Voluntario> ativos = voluntarios.findByAtivoTrueOrderByPessoa_NomeCompletoAsc();
        Set<UUID> idsAtivos = ativos.stream().map(Voluntario::getId).collect(Collectors.toSet());

        Map<UUID, List<Indisponivel>> porVoluntario = new HashMap<>();
        for (Indisponibilidade i : indisponibilidades.findByDataBetweenOrderByDataAsc(ym.atDay(1), ym.atEndOfMonth())) {
            porVoluntario.computeIfAbsent(i.getVoluntarioId(), k -> new ArrayList<>()).add(new Indisponivel(i.getData(), i.getPeriodo()));
        }
        Set<UUID> semRestricao = respostas.findByAnoAndMes(ym.getYear(), ym.getMonthValue()).stream()
                .filter(RespostaIndisponibilidade::isSemRestricao).map(RespostaIndisponibilidade::getVoluntarioId)
                .collect(Collectors.toSet());
        Map<UUID, Set<UUID>> irmaos = new HashMap<>();
        for (ParDeIrmaos par : pessoas.paresDeIrmaos()) {
            if (idsAtivos.contains(par.pessoaId()) && idsAtivos.contains(par.irmaoId())) {
                irmaos.computeIfAbsent(par.pessoaId(), k -> new LinkedHashSet<>()).add(par.irmaoId());
            }
        }

        return new ApoioEscala(ativos.stream().map(v -> {
            List<Indisponivel> datas = porVoluntario.getOrDefault(v.getId(), List.of());
            Situacao situacao = !datas.isEmpty() ? Situacao.COM_RESTRICAO
                    : semRestricao.contains(v.getId()) ? Situacao.SEM_RESTRICAO : Situacao.PENDENTE;
            return new ApoioVoluntario(v.getId(), situacao, datas, List.copyOf(irmaos.getOrDefault(v.getId(), Set.of())));
        }).toList());
    }

    private static YearMonth mesValido(int ano, int mes) {
        if (mes < 1 || mes > 12 || ano < 2000 || ano > 2100) throw new BadRequestException("Mês inválido.");
        return YearMonth.of(ano, mes);
    }

    private static String blankParaNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
