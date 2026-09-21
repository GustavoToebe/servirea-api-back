package br.com.servire.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token com rotation (seção 35/36 do plano mestre). Ver migration
 * V019.
 *
 * <p>Diferente de {@link UsuarioTenant}, esta entidade tem chave primária
 * simples ({@code id uuid PRIMARY KEY DEFAULT gen_random_uuid()}) — ao
 * reler a migration V019 nesta fase, ficou claro que ela nunca precisou de
 * {@code @MapsId}/chave composta (isso só se aplicava a
 * {@code usuario_tenant}); a nota da Fase 3 (seção 103) que agrupou as
 * duas como "adiadas por causa de @MapsId" foi imprecisa nesse ponto
 * específico para {@code refresh_token} — mas não fez mal nenhum adiar,
 * já que o fluxo de auth que a usa só existe agora mesmo, na Fase 5.</p>
 *
 * <p>{@code tokenHash} nunca guarda o token bruto — ver
 * {@code RefreshTokenService} para o algoritmo de hash usado e por quê
 * (SHA-256, não bcrypt/argon2, diferente de senha).</p>
 *
 * <p>{@code replacedBy} é guardado como UUID solto (não uma associação
 * {@code @ManyToOne} para si mesma) — não há necessidade de navegar esse
 * relacionamento como grafo de objetos, só de registrar auditoria de qual
 * token substituiu qual.</p>
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @Column(name = "ip")
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    protected RefreshToken() {
        // JPA
    }

    public RefreshToken(Usuario usuario, String tokenHash, Instant expiresAt, String ip, String userAgent) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.ip = ip;
        this.userAgent = userAgent;
    }

    public UUID getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public UUID getReplacedBy() {
        return replacedBy;
    }

    public void setReplacedBy(UUID replacedBy) {
        this.replacedBy = replacedBy;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public boolean isRevogado() {
        return revokedAt != null;
    }

    public boolean isExpirado(Instant agora) {
        return agora.isAfter(expiresAt);
    }
}
