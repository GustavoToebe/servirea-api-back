package br.com.servire.api.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Cada linha é uma paróquia (tenant) do SaaS — tabela global do "Master
 * lógico" (seção 25 do plano mestre), raiz da hierarquia multi-tenant
 * (seção 17/27). Ver migration V016.
 *
 * <p>{@code createdAt}/{@code updatedAt} são {@code insertable=false,
 * updatable=false} porque são geridos pelo banco (DEFAULT now() /
 * trigger {@code trg_tenant_updated_at}, não pela aplicação.</p>
 *
 * <p><b>Risco residual a verificar:</b> {@code status} usa
 * {@code @JdbcTypeCode(SqlTypes.NAMED_ENUM)} para mapear direto no tipo
 * nativo Postgres {@code tenant_status} (evita o erro clássico "column is
 * of type tenant_status but expression is of type character varying").
 * Este padrão foi confirmado como existente no Hibernate 6.5
 * ({@code PostgreSQLEnumJdbcType}), mas NÃO foi possível reconfirmar
 * contra a versão exata usada neste projeto (Hibernate ORM 7.4.5.Final)
 * por ter batido um limite de sessão de pesquisa no momento em que este
 * código foi escrito. Se o próximo {@code mvn clean verify} acusar erro
 * de schema/tipo nesta coluna, este é o primeiro lugar a checar.</p>
 */
@Entity
@Table(name = "tenant")
public class Tenant {

    public enum Status {
        ATIVO,
        TRIAL,
        BLOQUEADO,
        CANCELADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String nome;

    @Column(name = "razao_social")
    private String razaoSocial;

    private String cnpj;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Tenant() {
        // JPA
    }

    public Tenant(String codigo, String slug, String nome, Status status) {
        this.codigo = codigo;
        this.slug = slug;
        this.nome = nome;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getSlug() {
        return slug;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public void setRazaoSocial(String razaoSocial) {
        this.razaoSocial = razaoSocial;
    }

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
