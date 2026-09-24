package br.com.servire.api.escala;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acesso direto a {@link EscalaEvento} para o picker de candidatos
 * (seção 49) — o {@code GET /escalas/{eventoId}/candidatos} precisa do
 * evento (data/horário) e das vagas já preenchidas, sem carregar a
 * escala inteira.
 */
public interface EscalaEventoRepository extends JpaRepository<EscalaEvento, UUID> {

    @Override
    @EntityGraph(attributePaths = {"vagas", "vagas.voluntario.pessoa"})
    Optional<EscalaEvento> findById(UUID id);
}
