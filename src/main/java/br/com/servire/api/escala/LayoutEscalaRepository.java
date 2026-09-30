package br.com.servire.api.escala;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutEscalaRepository extends JpaRepository<LayoutEscala, UUID> {
    long countByTipoAndAtivoTrue(TipoEscala tipo);
    Optional<LayoutEscala> findFirstByTipoAndSistemaTrueAndAtivoTrue(TipoEscala tipo);
    Optional<LayoutEscala> findFirstByTipoAndPadraoTrueAndAtivoTrue(TipoEscala tipo);
    List<LayoutEscala> findByTipoAndPadraoTrue(TipoEscala tipo);
    boolean existsByTipoAndNomeIgnoreCase(TipoEscala tipo, String nome);
    boolean existsByTipoAndNomeIgnoreCaseAndIdNot(TipoEscala tipo, String nome, UUID id);
}
