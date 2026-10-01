package br.com.servire.api.evento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** O filtro da paróquia vem do {@code @TenantId}. */
public interface EventoRepository extends JpaRepository<Evento, UUID> {

    List<Evento> findAllByOrderByInicioDesc();

    /** Publicados com lembrete que começam entre {@code de} e {@code ate} (horário de Brasília). */
    @Query("""
            SELECT e FROM Evento e
            WHERE e.situacao = br.com.servire.api.evento.Evento.Situacao.PUBLICADO
              AND e.lembreteDias > 0 AND e.inicio >= :de AND e.inicio < :ate
            """)
    List<Evento> publicadosEntre(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate);
}
