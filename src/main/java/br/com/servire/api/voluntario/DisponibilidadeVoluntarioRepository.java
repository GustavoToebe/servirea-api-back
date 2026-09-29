package br.com.servire.api.voluntario;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DisponibilidadeVoluntarioRepository extends JpaRepository<DisponibilidadeVoluntario, UUID> {

    List<DisponibilidadeVoluntario> findByVoluntario_IdOrderByDiaSemanaAscDataAscPeriodoAsc(UUID voluntarioId);

    @EntityGraph(attributePaths = "voluntario")
    List<DisponibilidadeVoluntario> findByVoluntario_IdIn(Collection<UUID> voluntarioIds);

    @Override
    @EntityGraph(attributePaths = "voluntario")
    List<DisponibilidadeVoluntario> findAll();
}
