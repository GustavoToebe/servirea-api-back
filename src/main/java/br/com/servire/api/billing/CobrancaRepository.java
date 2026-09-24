package br.com.servire.api.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cobranças (tabela global, V029). Tudo em JPQL/derived query — mesmo sem
 * {@code @TenantId}, o projeto não abre exceção ao Native Query Gate
 * (seção 81).
 *
 * <p><b>Status sempre por parâmetro, nunca literal no JPQL</b> (achado de
 * 23/09/2026): com {@code NAMED_ENUM}, o Hibernate 7 renderiza
 * {@code Cobranca.Status.ABERTA} como {@code cast('ABERTA' as status)} —
 * nome do tipo derivado da classe Java, não do enum do Postgres
 * ({@code cobranca_status}) — e a query quebra com
 * {@code type "status" does not exist}. Parâmetro vai por
 * {@code setObject(OTHER)} e o Postgres infere o tipo certo.</p>
 */
public interface CobrancaRepository extends JpaRepository<Cobranca, UUID> {

    List<Cobranca> findByTenantIdOrderByCompetenciaInicioDesc(UUID tenantId);

    List<Cobranca> findByAssinaturaId(UUID assinaturaId);

    List<Cobranca> findByIdInAndTenantId(List<UUID> ids, UUID tenantId);

    boolean existsByTenantIdAndStatusAndVencimentoBefore(UUID tenantId, Cobranca.Status status, LocalDate data);

    /**
     * Atraso por paróquia numa query só (listagem do backoffice, sem N+1):
     * {@code [tenantId, quantidadeVencidas, vencimentoMaisAntigo]}.
     */
    @Query("""
            SELECT c.tenantId, COUNT(c), MIN(c.vencimento)
            FROM Cobranca c
            WHERE c.status = :status
              AND c.vencimento < :hoje
            GROUP BY c.tenantId
            """)
    List<Object[]> resumoAtrasoPorTenant(@Param("status") Cobranca.Status status, @Param("hoje") LocalDate hoje);

    @Query("""
            SELECT COUNT(DISTINCT c.tenantId)
            FROM Cobranca c
            WHERE c.status = :status
              AND c.vencimento < :hoje
            """)
    long contarTenantsEmAtraso(@Param("status") Cobranca.Status status, @Param("hoje") LocalDate hoje);

    @Query("""
            SELECT COALESCE(SUM(c.valorPago), 0)
            FROM Cobranca c
            WHERE c.status = :status
              AND c.pagoEm >= :de AND c.pagoEm <= :ate
            """)
    BigDecimal somarRecebido(@Param("status") Cobranca.Status status, @Param("de") LocalDate de,
                             @Param("ate") LocalDate ate);
}
