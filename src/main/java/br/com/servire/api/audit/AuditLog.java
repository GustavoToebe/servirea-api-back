package br.com.servire.api.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Trilha de auditoria (Fase 11 do plano mestre, seção 59) — mapeia
 * {@code audit_log} (V026). Entidade sem setters de propósito: um registro
 * de auditoria é gravado uma vez e nunca alterado depois (só criado/lido).
 *
 * <p>{@code changedFields} mapeia {@code text[]} (ver comentário da
 * migration V026 explicando a escolha de array nativo em vez de
 * {@code jsonb}) — {@code @JdbcTypeCode(SqlTypes.ARRAY)} sozinho basta
 * aqui porque os elementos são {@code String} livre, não um ENUM do
 * Postgres (diferente de
 * {@link br.com.servire.api.voluntario.Voluntario#getFuncoesHabilitadas()},
 * que precisou do combo extra {@code @Enumerated} +
 * {@code @ColumnTransformer} por causa do bug do Hibernate 7.x com array
 * de ENUM nativo).</p>
 *
 * <p>{@code userId} é nullable — ações do formulário público de inscrição
 * (seção 44) não têm um {@link br.com.servire.api.auth.Usuario} autenticado
 * por trás (ver {@link AuditLogService#registrar}).</p>
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String acao;

    @Column(nullable = false)
    private String entidade;

    @Column(name = "entidade_id", nullable = false)
    private UUID entidadeId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "changed_fields")
    private String[] changedFields;

    private String ip;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(UUID userId, String acao, String entidade, UUID entidadeId, String[] changedFields,
                     String ip, String requestId) {
        this.userId = userId;
        this.acao = Objects.requireNonNull(acao, "acao não pode ser nula");
        this.entidade = Objects.requireNonNull(entidade, "entidade não pode ser nula");
        this.entidadeId = Objects.requireNonNull(entidadeId, "entidadeId não pode ser nulo");
        this.changedFields = changedFields;
        this.ip = ip;
        this.requestId = requestId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
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

    public String[] getChangedFields() {
        return changedFields;
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
