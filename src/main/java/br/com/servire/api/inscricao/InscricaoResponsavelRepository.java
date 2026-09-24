package br.com.servire.api.inscricao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Só usado a partir de {@link InscricaoService} — nunca diretamente
 * de um controller.
 */
public interface InscricaoResponsavelRepository extends JpaRepository<InscricaoResponsavel, UUID> {

    List<InscricaoResponsavel> findByInscricao_Id(UUID inscricaoId);
}
