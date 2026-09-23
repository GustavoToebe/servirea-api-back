package br.com.servire.api.backoffice.dto;

import br.com.servire.api.backoffice.BackofficeLog;

import java.time.Instant;
import java.util.UUID;

public record BackofficeLogResponse(
        UUID id,
        UUID userId,
        UUID tenantAlvoId,
        String acao,
        String entidade,
        UUID entidadeId,
        String ip,
        String requestId,
        Instant createdAt
) {

    public static BackofficeLogResponse de(BackofficeLog log) {
        return new BackofficeLogResponse(
                log.getId(), log.getUserId(), log.getTenantAlvoId(),
                log.getAcao(), log.getEntidade(), log.getEntidadeId(),
                log.getIp(), log.getRequestId(), log.getCreatedAt());
    }
}
