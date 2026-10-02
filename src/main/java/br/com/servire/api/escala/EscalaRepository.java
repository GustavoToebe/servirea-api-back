package br.com.servire.api.escala;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/** Filtros da listagem em {@code EscalaService.buscar} (Specification). */
public interface EscalaRepository extends JpaRepository<Escala, UUID>, JpaSpecificationExecutor<Escala> {
    boolean existsByLayoutId(UUID layoutId);

    /** Serializa respostas com reabertura, cancelamento e alteração da alocação. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Escala e where e.id=:id")
    java.util.Optional<Escala> bloquear(@org.springframework.data.repository.query.Param("id") UUID id);
}
