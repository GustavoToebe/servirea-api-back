package br.com.servire.api.voluntario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repositório tenant-aware: toda query gerada aqui recebe automaticamente
 * o filtro {@code WHERE tenant_id = ?} do Hibernate via {@code @TenantId}
 * em {@link Voluntario} - inclusive {@link #findById}, que é exatamente o
 * cenário do teste crítico de isolamento (seção 78: buscar por ID de
 * outro tenant não deve retornar nada), seção 79/80 do plano mestre.
 *
 * <p>{@link #buscar} usa JPQL (não SQL nativo) de propósito — seção 81
 * do plano mestre ("Native Query Gate"): {@code nativeQuery = true}
 * bypassa o filtro {@code @TenantId} do Hibernate, então qualquer filtro
 * de listagem tem que continuar em JPQL/Criteria para herdar o
 * isolamento automaticamente.</p>
 *
 * <p>Filtro por {@code funcoes_habilitadas} (array de enum) fica de fora
 * por enquanto — é usado pelo picker de candidatos da Fase 9 (seção 49),
 * ainda não implementado; adicionar quando esse módulo existir.</p>
 */
public interface VoluntarioRepository extends JpaRepository<Voluntario, UUID> {

    @Query("""
            SELECT v FROM Voluntario v
            WHERE (:ativo IS NULL OR v.ativo = :ativo)
              AND (:tipo IS NULL OR v.tipo = :tipo)
              AND (:nome IS NULL OR LOWER(v.nomeCompleto) LIKE LOWER(CONCAT('%', :nome, '%')))
            ORDER BY v.nomeCompleto ASC
            """)
    List<Voluntario> buscar(@Param("ativo") Boolean ativo, @Param("tipo") TipoVoluntario tipo, @Param("nome") String nome);

    long countByAtivo(boolean ativo);
}
