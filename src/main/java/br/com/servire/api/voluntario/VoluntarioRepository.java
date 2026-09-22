package br.com.servire.api.voluntario;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório tenant-aware: toda query gerada aqui recebe automaticamente
 * o filtro {@code WHERE tenant_id = ?} do Hibernate via {@code @TenantId}
 * em {@link Voluntario} - inclusive {@link #findById}, que é exatamente o
 * cenário do teste crítico de isolamento (seção 78: buscar por ID de
 * outro tenant não deve retornar nada), seção 79/80 do plano mestre.
 *
 * <p>A listagem com filtros opcionais ({@code ativo}/{@code tipo}/{@code nome})
 * NÃO usa JPQL com {@code (:param IS NULL OR ...)}. No Hibernate 7 +
 * Postgres, parâmetro nulo nessa forma vira bind sem tipo JDBC: primeiro
 * {@code function lower(bytea) does not exist} no {@code CONCAT} do nome,
 * e depois de um {@code CAST} o erro só mudava para
 * {@code could not determine data type of parameter $4} no
 * {@code :tipo IS NULL}. Criteria/Specification só inclui o predicado
 * quando o filtro veio preenchido — continua JPQL/Criteria (Native Query
 * Gate, seção 81), então o {@code @TenantId} segue valendo.</p>
 *
 * <p>O picker de candidatos (seção 49) filtra {@code funcoes_habilitadas}
 * em memória em {@code EscalaService#listarCandidatos} — array de ENUM
 * nativo no Criteria/JPQL do Hibernate 7 ainda é o mapeamento mais
 * arriscado deste projeto (ver javadoc de {@link Voluntario}).</p>
 */
public interface VoluntarioRepository extends JpaRepository<Voluntario, UUID>, JpaSpecificationExecutor<Voluntario> {

    /**
     * {@code VoluntarioResponse.de} lê {@code responsaveis} no controller,
     * já com a transação do service fechada ({@code open-in-view: false}).
     * Sem o graph, POST /foto (e GET /{id}, PATCH /ativo) estouravam
     * {@code LazyInitializationException} depois do upload já ter
     * funcionado no Supabase.
     */
    @Override
    @EntityGraph(attributePaths = "responsaveis")
    Optional<Voluntario> findById(UUID id);

    long countByAtivo(boolean ativo);

    List<Voluntario> findByAtivoTrueOrderByNomeCompletoAsc();
}
