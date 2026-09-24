package br.com.servire.api.pessoa;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PessoaRepository extends JpaRepository<Pessoa, UUID>, JpaSpecificationExecutor<Pessoa> {

    @Override
    @EntityGraph(attributePaths = {"emails", "telefones", "responsaveis.responsavel", "dependentes.voluntario", "voluntario"})
    Optional<Pessoa> findById(UUID id);

    @Query("""
            SELECT DISTINCT p FROM Pessoa p
            JOIN p.emails e
            WHERE e.principal = true
              AND lower(e.email) = lower(:email)
            """)
    Optional<Pessoa> findPorEmailPrincipal(@Param("email") String email);
}
