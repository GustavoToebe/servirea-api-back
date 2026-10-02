package br.com.servire.api.escala;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RespostaIndisponibilidadeRepository extends JpaRepository<RespostaIndisponibilidade, UUID> {

    List<RespostaIndisponibilidade> findByAnoAndMes(int ano, int mes);

    List<RespostaIndisponibilidade> findByVoluntarioIdAndAnoAndMes(UUID voluntarioId,int ano,int mes);
    void deleteByVoluntarioIdAndAnoAndMes(UUID voluntarioId,int ano,int mes);
    void deleteByAnoAndMes(int ano, int mes);
}
