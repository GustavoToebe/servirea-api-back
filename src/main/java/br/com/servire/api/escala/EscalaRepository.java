package br.com.servire.api.escala;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/** Filtros da listagem em {@code EscalaService.buscar} (Specification). */
public interface EscalaRepository extends JpaRepository<Escala, UUID>, JpaSpecificationExecutor<Escala> {
    boolean existsByLayoutId(UUID layoutId);
}
