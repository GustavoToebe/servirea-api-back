package br.com.servire.api.evento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventoFotoRepository extends JpaRepository<EventoFoto, UUID> {

    List<EventoFoto> findByEventoIdOrderByCapaDescCreatedAtAsc(UUID eventoId);

    long countByEventoId(UUID eventoId);
}
