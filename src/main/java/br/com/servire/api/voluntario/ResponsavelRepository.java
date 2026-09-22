package br.com.servire.api.voluntario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * {@code responsaveis} não é tenant-aware por conta própria (ver javadoc
 * de {@link Responsavel}) — nunca usar {@link #findById} vindo de um
 * parâmetro de rota sem antes confirmar, via {@link VoluntarioRepository},
 * que o {@code voluntario_id} pertence ao tenant atual. Este repositório
 * só deve ser acessado a partir de {@link VoluntarioService}, nunca
 * diretamente de um controller.
 */
public interface ResponsavelRepository extends JpaRepository<Responsavel, UUID> {

    List<Responsavel> findByVoluntario_Id(UUID voluntarioId);
}
