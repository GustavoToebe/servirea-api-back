package br.com.servire.api.comunicacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Layout de mensagem (e-mail ou WhatsApp) com tags substituíveis por dados
 * do destinatário no momento do envio. Ver PLANO-004 e migration V042.
 */
@Entity
@Table(name = "layout_envio")
public class Layout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_layout", nullable = false, length = 30)
    private TipoLayout tipoLayout;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_envio", nullable = false, length = 20)
    private TipoEnvio tipoEnvio;

    @Column(length = 200)
    private String assunto;

    @Column(nullable = false, columnDefinition = "text")
    private String conteudo;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Layout() {
    }

    public Layout(String nome, TipoLayout tipoLayout, TipoEnvio tipoEnvio, String assunto, String conteudo, boolean ativo) {
        this.nome = nome;
        this.tipoLayout = tipoLayout;
        this.tipoEnvio = tipoEnvio;
        this.assunto = assunto;
        this.conteudo = conteudo;
        this.ativo = ativo;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public TipoLayout getTipoLayout() { return tipoLayout; }
    public void setTipoLayout(TipoLayout tipoLayout) { this.tipoLayout = tipoLayout; }
    public TipoEnvio getTipoEnvio() { return tipoEnvio; }
    public void setTipoEnvio(TipoEnvio tipoEnvio) { this.tipoEnvio = tipoEnvio; }
    public String getAssunto() { return assunto; }
    public void setAssunto(String assunto) { this.assunto = assunto; }
    public String getConteudo() { return conteudo; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
