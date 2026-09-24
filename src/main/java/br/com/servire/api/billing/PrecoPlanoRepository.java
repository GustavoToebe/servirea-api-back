package br.com.servire.api.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrecoPlanoRepository extends JpaRepository<PrecoPlano, UUID> {

    /** Preço vigente numa data: o de maior {@code vigenteDesde} que já começou (seção 63). */
    Optional<PrecoPlano> findFirstByPlanoIdAndPeriodicidadeAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
            UUID planoId, Periodicidade periodicidade, LocalDate data);

    boolean existsByPlanoIdAndPeriodicidadeAndVigenteDesde(UUID planoId, Periodicidade periodicidade,
                                                           LocalDate vigenteDesde);

    List<PrecoPlano> findByPlanoIdOrderByVigenteDesdeDesc(UUID planoId);
}
