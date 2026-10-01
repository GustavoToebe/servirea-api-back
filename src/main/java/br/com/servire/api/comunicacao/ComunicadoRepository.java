package br.com.servire.api.comunicacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ComunicadoRepository extends JpaRepository<Comunicado, UUID>, JpaSpecificationExecutor<Comunicado> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Comunicado c where c.id=:id")
    java.util.Optional<Comunicado> buscarParaAlterar(@org.springframework.data.repository.query.Param("id") UUID id);
}
