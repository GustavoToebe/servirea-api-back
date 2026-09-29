package br.com.servire.api.escala;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "layout_escala")
public class LayoutEscala {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TipoEscala tipo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<ColunaEscalaDto> colunas;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false, updatable = false)
    private boolean sistema = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected LayoutEscala() {}

    public LayoutEscala(String nome, TipoEscala tipo, List<ColunaEscalaDto> colunas, boolean sistema) {
        this.nome = nome;
        this.tipo = tipo;
        this.colunas = colunas;
        this.sistema = sistema;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public TipoEscala getTipo() { return tipo; }
    public void setTipo(TipoEscala tipo) { this.tipo = tipo; }
    public List<ColunaEscalaDto> getColunas() { return colunas; }
    public void setColunas(List<ColunaEscalaDto> colunas) { this.colunas = colunas; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public boolean isSistema() { return sistema; }
    public void setSistema(boolean sistema) { this.sistema = sistema; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
