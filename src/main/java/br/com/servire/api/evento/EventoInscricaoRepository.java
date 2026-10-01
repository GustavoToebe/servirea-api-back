package br.com.servire.api.evento;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventoInscricaoRepository extends JpaRepository<EventoInscricao, UUID> {

    @EntityGraph(attributePaths = {"pessoa"})
    List<EventoInscricao> findByEventoIdOrderByCreatedAtAsc(UUID eventoId);

    long countByEventoId(UUID eventoId);

    boolean existsByEventoIdAndPessoa_Id(UUID eventoId, UUID pessoaId);

    /** Quem autorizou WhatsApp e ainda não recebeu lembrete. */
    @EntityGraph(attributePaths = {"pessoa"})
    List<EventoInscricao> findByEventoIdAndAutorizaWhatsappTrueAndLembreteEnviadoEmIsNull(UUID eventoId);
}
