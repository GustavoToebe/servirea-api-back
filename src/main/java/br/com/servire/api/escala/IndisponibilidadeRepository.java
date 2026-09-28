package br.com.servire.api.escala;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IndisponibilidadeRepository extends JpaRepository<Indisponibilidade, UUID> {

    List<Indisponibilidade> findByDataBetweenOrderByDataAsc(LocalDate de, LocalDate ate);

    void deleteByDataBetween(LocalDate de, LocalDate ate);
}
