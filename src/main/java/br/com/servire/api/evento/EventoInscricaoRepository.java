package br.com.servire.api.evento;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventoInscricaoRepository extends JpaRepository<EventoInscricao, UUID> {

    @EntityGraph(attributePaths = {"pessoa"})
    List<EventoInscricao> findByEventoIdOrderByCreatedAtAsc(UUID eventoId);

    long countByEventoId(UUID eventoId);

    boolean existsByEventoIdAndPessoa_Id(UUID eventoId, UUID pessoaId);

    /** Quem autorizou WhatsApp e ainda não recebeu o lembrete deste marco (nunca, ou só um de marco anterior). */
    @EntityGraph(attributePaths = {"pessoa"})
    @Query("""
            SELECT i FROM EventoInscricao i
            WHERE i.eventoId = :eventoId AND i.autorizaWhatsapp = true
              AND (i.lembreteEnviadoEm IS NULL OR i.lembreteEnviadoEm < :marco)
            """)
    List<EventoInscricao> pendentesDeLembreteWhatsapp(@Param("eventoId") UUID eventoId, @Param("marco") Instant marco);

    /** Quem ainda não recebeu por e-mail o lembrete deste marco. */
    @EntityGraph(attributePaths = {"pessoa"})
    @Query("""
            SELECT i FROM EventoInscricao i
            WHERE i.eventoId = :eventoId AND (i.lembreteEmailEnviadoEm IS NULL OR i.lembreteEmailEnviadoEm < :marco)
            """)
    List<EventoInscricao> pendentesDeLembreteEmail(@Param("eventoId") UUID eventoId, @Param("marco") Instant marco);
}
