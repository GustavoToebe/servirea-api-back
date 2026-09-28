package br.com.servire.api.comunicacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Instância do Evolution Go da paróquia (uma por paróquia, V043). A chave é o próprio
 * {@code tenant_id}, sempre lido do {@code TenantContext}; o token nunca volta na API nem vai para log.
 */
@Entity
@Table(name = "paroquia_whatsapp")
public class ParoquiaWhatsapp {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 120)
    private String instancia;

    @Column(nullable = false, length = 300)
    private String token;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ParoquiaWhatsapp() {
    }

    public ParoquiaWhatsapp(UUID tenantId, String instancia, String token, boolean ativo) {
        this.tenantId = tenantId;
        this.instancia = instancia;
        this.token = token;
        this.ativo = ativo;
    }

    public UUID getTenantId() { return tenantId; }
    public String getInstancia() { return instancia; }
    public String getToken() { return token; }
    public boolean isAtivo() { return ativo; }

    void atualizar(String instancia, String token, boolean ativo) {
        this.instancia = instancia;
        if (token != null && !token.isBlank()) this.token = token;
        this.ativo = ativo;
        this.updatedAt = Instant.now();
    }
}
