package br.com.servire.api.voluntario;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoluntarioRepository extends JpaRepository<Voluntario, UUID>, JpaSpecificationExecutor<Voluntario> {

    @Override
    @EntityGraph(attributePaths = "pessoa")
    Optional<Voluntario> findById(UUID id);

    long countByAtivo(boolean ativo);

    @Query("select v.ativo, count(v) from Voluntario v group by v.ativo")
    List<Object[]> contarAgrupadoPorAtivo();

    @EntityGraph(attributePaths = "pessoa")
    List<Voluntario> findByAtivoTrueOrderByPessoa_NomeCompletoAsc();
}
