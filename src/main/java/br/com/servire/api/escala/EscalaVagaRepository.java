package br.com.servire.api.escala;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso direto a {@link EscalaVaga} (Fase 11) — até então a vaga só era
 * lida/gravada indireta, como parte do agregado {@code Escala} (cascade a
 * partir de {@link EscalaRepository}). O controle de faltas (seção 131.5
 * item 11) precisa achar/gravar UMA vaga específica sem carregar a escala
 * inteira, daí este repositório dedicado. O picker de compromissos
 * ({@code GET /voluntarios/{id}/commitments}) também lê por aqui, com
 * fetch de evento/escala, em vez de consultar a view
 * {@code vw_voluntario_compromissos} em SQL nativo (seção 81).
 */
public interface EscalaVagaRepository extends JpaRepository<EscalaVaga, UUID> {

    @EntityGraph(attributePaths = {"evento", "evento.escala", "voluntario"})
    List<EscalaVaga> findByVoluntario_IdOrderByEvento_DataAscEvento_HorarioAsc(UUID voluntarioId);

    @Override
    @EntityGraph(attributePaths = {"evento", "evento.escala", "evento.vagas", "evento.vagas.voluntario", "voluntario"})
    Optional<EscalaVaga> findById(UUID id);
}
