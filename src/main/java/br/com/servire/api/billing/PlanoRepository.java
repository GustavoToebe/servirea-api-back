package br.com.servire.api.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanoRepository extends JpaRepository<Plano, UUID> {

    boolean existsByCodigo(String codigo);

    List<Plano> findAllByOrderByNomeAsc();
}
