package br.com.servire.api.escala;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutEscalaRepository extends JpaRepository<LayoutEscala, UUID> {
    long countByTipoAndAtivoTrue(TipoEscala tipo);
    Optional<LayoutEscala> findFirstByTipoAndSistemaTrueAndAtivoTrue(TipoEscala tipo);
}
