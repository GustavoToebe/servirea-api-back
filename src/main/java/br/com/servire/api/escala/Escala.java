package br.com.servire.api.escala;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Uma escala de serviço (semanal ou mensal) — mapeia {@code escalas} (V005
 * + {@code tenant_id} de V021 + {@code version} de V023), Fase 9, seção
 * 9.2/46/47/109 do plano mestre.
 *
 * <p>{@code createdBy} é um {@code UUID} simples (não {@code @ManyToOne}
 * para {@code Usuario}) — mesmo motivo de {@code Inscricao.aprovadoPor}:
 * só precisamos gravar "quem", a FK do banco (repontada de
 * {@code auth.users} para {@code usuario} pela V023) garante a
 * integridade.</p>
 *
 * <p>{@code @Version private Long version} implementa o controle
 * otimista da seção 47 ("HTTP 409 em conflito de edição concorrente") — a
 * coluna só passou a existir no banco na V023; antes disso essa anotação
 * quebraria o {@code ddl-auto: validate}. {@link EscalaService} faz uma
 * checagem explícita de versão (não só confia no
 * {@code OptimisticLockException} do Hibernate) para devolver a mensagem
 * de negócio certa ao cliente — ver {@link
 * br.com.servire.api.web.GlobalExceptionHandler} para o
 * {@code ObjectOptimisticLockingFailureException} como rede de segurança
 * contra uma corrida de verdade entre duas requisições concorrentes.</p>
 */
@Entity
@Table(name = "escalas")
public class Escala {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Número curto por paróquia para ditar e copiar (V038); o gatilho do banco numera no insert. */
    @org.hibernate.annotations.Generated
    @Column(insertable = false, updatable = false)
    private Long sequencial;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TipoEscala tipo;

    private Integer ano;

    private Integer mes;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private StatusEscala status = StatusEscala.RASCUNHO;

    private String observacao;

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "escala", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("data ASC, horario ASC")
    private List<EscalaEvento> eventos = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<ColunaEscalaDto> colunas;

    @Column(name = "layout_id")
    private UUID layoutId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Escala() {
        // JPA
    }

    public Escala(String titulo, TipoEscala tipo) {
        this.titulo = titulo;
        this.tipo = tipo;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public TipoEscala getTipo() {
        return tipo;
    }

    public void setTipo(TipoEscala tipo) {
        this.tipo = tipo;
    }

    public Integer getAno() {
        return ano;
    }

    public void setAno(Integer ano) {
        this.ano = ano;
    }

    public Integer getMes() {
        return mes;
    }

    public void setMes(Integer mes) {
        this.mes = mes;
    }

    public StatusEscala getStatus() {
        return status;
    }

    public void setStatus(StatusEscala status) {
        this.status = status;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Long getVersion() {
        return version;
    }

    public List<EscalaEvento> getEventos() {
        return eventos;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
    public Long getSequencial() {
        return sequencial;
    }

    public List<ColunaEscalaDto> getColunas() {
        return colunas;
    }

    public void setColunas(List<ColunaEscalaDto> colunas) {
        this.colunas = colunas;
    }

    public UUID getLayoutId() {
        return layoutId;
    }

    public void setLayoutId(UUID layoutId) {
        this.layoutId = layoutId;
    }
}
