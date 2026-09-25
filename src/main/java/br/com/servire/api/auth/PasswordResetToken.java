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
 * Token de "esqueci minha senha" (seção 32 do plano mestre — POST
 * /auth/forgot-password e POST /auth/reset-password). Ver migration V022,
 * criada só agora, na Fase 5 — não existia no schema da Fase 3 porque essa
 * fase não incluía o fluxo de autenticação, só as tabelas de dados
 * (seção 103).
 *
 * <p>Mesmo padrão de {@link RefreshToken}: nunca guardar o token bruto, só
 * o hash; uso único, marcado por {@code usedAt}.</p>
 */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

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

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(nullable = false)
    private String finalidade = "RESET";

    protected PasswordResetToken() {
        // JPA
    }

    public PasswordResetToken(Usuario usuario, String tokenHash, Instant expiresAt) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
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

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public void setFinalidade(String finalidade) {
        this.finalidade = finalidade;
    }

    public boolean isUsado() {
        return usedAt != null;
    }

    public boolean isExpirado(Instant agora) {
        return agora.isAfter(expiresAt);
    }
}
