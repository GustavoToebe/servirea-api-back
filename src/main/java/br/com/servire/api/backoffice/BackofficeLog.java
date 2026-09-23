package br.com.servire.api.backoffice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Trilha das ações do painel do operador (seção 111). Tabela global —
 * sem {@code @TenantId}. Distinta de {@code audit_log} (tenant-aware):
 * listar "todas as paróquias" por SQL nativo em tabela filtrada pelo
 * Hibernate é o Native Query Gate (seção 81).
 *
 * <p>Sem setters: registro é gravado uma vez e nunca alterado.</p>
 */
@Entity
@Table(name = "backoffice_log")
public class BackofficeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "tenant_alvo_id")
    private UUID tenantAlvoId;

    @Column(nullable = false)
    private String acao;

    @Column(nullable = false)
    private String entidade;

    @Column(name = "entidade_id", nullable = false)
    private UUID entidadeId;

    private String ip;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected BackofficeLog() {
        // JPA
    }

    public BackofficeLog(UUID userId, UUID tenantAlvoId, String acao, String entidade, UUID entidadeId,
                          String ip, String requestId) {
        this.userId = userId;
        this.tenantAlvoId = tenantAlvoId;
        this.acao = Objects.requireNonNull(acao, "acao não pode ser nula");
        this.entidade = Objects.requireNonNull(entidade, "entidade não pode ser nula");
        this.entidadeId = Objects.requireNonNull(entidadeId, "entidadeId não pode ser nulo");
        this.ip = ip;
        this.requestId = requestId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantAlvoId() {
        return tenantAlvoId;
    }

    public String getAcao() {
        return acao;
    }

    public String getEntidade() {
        return entidade;
    }

    public UUID getEntidadeId() {
        return entidadeId;
    }

    public String getIp() {
        return ip;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
