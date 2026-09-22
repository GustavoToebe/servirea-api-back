package br.com.servire.api.voluntario;

import br.com.servire.api.voluntario.dto.DisponibilidadeVoluntarioRequest;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Regras de negócio de disponibilidade do voluntário (Fase 11, seção
 * 131.5 item 12 do plano mestre).
 */
@Service
public class DisponibilidadeVoluntarioService {

    private final DisponibilidadeVoluntarioRepository disponibilidadeRepository;
    private final VoluntarioRepository voluntarioRepository;

    public DisponibilidadeVoluntarioService(DisponibilidadeVoluntarioRepository disponibilidadeRepository,
                                             VoluntarioRepository voluntarioRepository) {
        this.disponibilidadeRepository = disponibilidadeRepository;
        this.voluntarioRepository = voluntarioRepository;
    }

    @Transactional(readOnly = true)
    public List<DisponibilidadeVoluntario> listar(UUID voluntarioId) {
        buscarVoluntario(voluntarioId);
        return disponibilidadeRepository.findByVoluntario_IdOrderByDiaSemanaAscDataAscPeriodoAsc(voluntarioId);
    }

    /**
     * A dupla checagem de "exatamente um entre diaSemana/data" (aqui E no
     * construtor de {@link DisponibilidadeVoluntario}) é deliberada — aqui
     * porque é a mensagem que o cliente HTTP recebe (400 com texto
     * específico); no construtor, como rede de segurança contra qualquer
     * outro chamador futuro que monte a entidade sem passar por este
     * serviço (mesma filosofia de {@link br.com.servire.api.auth.UsuarioTenant}).
     *
     * <p><b>Bug real #14 (22/09/2026, ver README.md):</b> usava
     * {@code disponibilidadeRepository.save(...)} em vez de
     * {@code saveAndFlush(...)} — {@code save()} de uma entidade nova só
     * chama {@code entityManager.persist(...)}, que agenda o {@code INSERT}
     * sem necessariamente enviá-lo ao banco na hora; com
     * {@code FlushMode.AUTO} (padrão), o Hibernate pode adiar esse flush
     * até o COMMIT da transação — que só acontece depois que este método
     * (e seu {@code try/catch}) já retornou. Resultado: o índice único
     * parcial {@code ux_disponibilidade_recorrente}/{@code ux_disponibilidade_pontual}
     * (V025) violado no banco virava um {@code DataIntegrityViolationException}
     * cru pro cliente (500 genérico), nunca o {@code ConflictException} (409)
     * que este método tenta garantir — o {@code catch} nunca disparava,
     * porque a exceção só aparecia DEPOIS do método já ter retornado
     * normalmente. Mesma categoria de problema (timing de flush do
     * Hibernate) já documentada no Bug real #10
     * ({@code VoluntarioService.substituirResponsaveis}, seção 106), só
     * que lá era um {@code DELETE} atrasado, aqui é um {@code INSERT}.
     * Corrigido trocando para {@code saveAndFlush(...)}, que força o
     * {@code INSERT} (e portanto a violação de constraint, se houver) a
     * acontecer sincronamente, dentro do {@code try}. Encontrado só agora
     * porque nenhum teste anterior chamava {@code criar} duas vezes com o
     * mesmo dia/período para o mesmo voluntário.</p>
     */
    @Transactional
    public DisponibilidadeVoluntario criar(UUID voluntarioId, DisponibilidadeVoluntarioRequest request) {
        Voluntario voluntario = buscarVoluntario(voluntarioId);
        if ((request.diaSemana() == null) == (request.data() == null)) {
            throw new BadRequestException(
                    "Informe exatamente um entre diaSemana (disponibilidade recorrente) e data (exceção pontual).");
        }
        DisponibilidadeVoluntario disponibilidade = new DisponibilidadeVoluntario(
                voluntario, request.diaSemana(), request.data(), request.periodo(), request.observacao());
        try {
            return disponibilidadeRepository.saveAndFlush(disponibilidade);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Já existe uma disponibilidade cadastrada para este mesmo dia/data e período.");
        }
    }

    @Transactional
    public void excluir(UUID voluntarioId, UUID id) {
        DisponibilidadeVoluntario disponibilidade = disponibilidadeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Disponibilidade não encontrada."));
        // Nunca revelar que a disponibilidade existe (para outro voluntário
        // do mesmo tenant, ou de outro tenant) — mesmo 404 dos dois casos.
        if (!disponibilidade.getVoluntario().getId().equals(voluntarioId)) {
            throw new ResourceNotFoundException("Disponibilidade não encontrada.");
        }
        disponibilidadeRepository.delete(disponibilidade);
    }

    private Voluntario buscarVoluntario(UUID id) {
        return voluntarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voluntário não encontrado."));
    }
}
