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
            return disponibilidadeRepository.save(disponibilidade);
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
