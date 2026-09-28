package br.com.servire.api.comunicacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ComunicadoRepository extends JpaRepository<Comunicado, UUID>, JpaSpecificationExecutor<Comunicado> {
}
