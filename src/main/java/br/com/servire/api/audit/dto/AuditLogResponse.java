package br.com.servire.api.audit.dto;

import br.com.servire.api.audit.AuditLog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditLogResponse(UUID id, UUID userId, String acao, String entidade, UUID entidadeId,
                                List<String> changedFields, String ip, String requestId, Instant createdAt) {

    public static AuditLogResponse de(AuditLog a) {
        return new AuditLogResponse(
                a.getId(), a.getUserId(), a.getAcao(), a.getEntidade(), a.getEntidadeId(),
                a.getChangedFields() == null ? List.of() : List.of(a.getChangedFields()),
                a.getIp(), a.getRequestId(), a.getCreatedAt());
    }
}
