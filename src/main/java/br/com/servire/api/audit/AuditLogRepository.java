package br.com.servire.api.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEntidadeAndEntidadeIdOrderByCreatedAtDesc(String entidade, UUID entidadeId);

    /**
     * {@code Top200} em vez de {@link org.springframework.data.domain.Pageable}
     * — nenhum outro endpoint do projeto pagina (seção 15: manter a stack
     * enxuta) e o volume de auditoria de uma única paróquia não justifica
     * introduzir paginação de verdade nesta rodada.
     */
    List<AuditLog> findTop200ByOrderByCreatedAtDesc();
}
