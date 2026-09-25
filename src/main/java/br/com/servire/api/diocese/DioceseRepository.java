package br.com.servire.api.diocese;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DioceseRepository extends JpaRepository<Diocese, UUID> {

    Optional<Diocese> findFirstByNomeIgnoreCase(String nome);

    /**
     * Cria a diocese se ainda não existe outra com o mesmo nome (sem
     * diferenciar maiúsculas). {@code ON CONFLICT} em vez de
     * {@code saveAndFlush} + captura de {@code DataIntegrityViolationException}:
     * duas paróquias digitando a mesma diocese ao mesmo tempo não derrubam a
     * transação de nenhuma. {@code diocese} é tabela global, fora do Native
     * Query Gate.
     */
    @Modifying
    @Query(value = "INSERT INTO public.diocese (nome) VALUES (:nome) "
            + "ON CONFLICT ((lower(nome))) DO NOTHING", nativeQuery = true)
    int inserirSeNaoExiste(@Param("nome") String nome);

    /** Dioceses ligadas a pelo menos uma paróquia, para sugerir no cadastro. */
    @Query("SELECT d FROM Diocese d WHERE EXISTS (SELECT 1 FROM Tenant t WHERE t.diocese = d) ORDER BY d.nome")
    List<Diocese> listarEmUso();
}
