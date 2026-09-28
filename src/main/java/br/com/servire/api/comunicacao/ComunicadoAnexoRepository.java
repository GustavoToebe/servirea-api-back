package br.com.servire.api.comunicacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComunicadoAnexoRepository extends JpaRepository<ComunicadoAnexo, UUID> {

    List<ComunicadoAnexo> findByComunicadoIdOrderByNome(UUID comunicadoId);
}
