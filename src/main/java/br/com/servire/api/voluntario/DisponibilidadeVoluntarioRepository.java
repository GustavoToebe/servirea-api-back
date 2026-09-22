package br.com.servire.api.voluntario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisponibilidadeVoluntarioRepository extends JpaRepository<DisponibilidadeVoluntario, UUID> {

    List<DisponibilidadeVoluntario> findByVoluntario_IdOrderByDiaSemanaAscDataAscPeriodoAsc(UUID voluntarioId);
}
