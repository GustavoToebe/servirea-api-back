package br.com.servire.api.auth;

import br.com.servire.api.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * Vínculo N:N usuário&lt;-&gt;paróquia (seção 29 do plano mestre) — base do
 * Kill Switch (seção 28) e da troca de paróquia ativa (seção 30). Ver
 * migration V018.
 *
 * <p><b>Por que só agora, na Fase 5, e não já na Fase 3 (seção 103):</b>
 * esta entidade tem chave primária composta ({@code PRIMARY KEY
 * (usuario_id, tenant_id)}, sem coluna {@code id} própria), mapeada via
 * {@code @EmbeddedId} + {@code @MapsId} duplo — o Hibernate obtém os dois
 * componentes da chave ({@code usuarioId}/{@code tenantId}) diretamente
 * dos IDs de {@link Usuario} e {@link Tenant} já associados. Isso só é
 * seguro de usar quando existe um fluxo real (login/cadastro) que já
 * garante a ordem certa — {@code usuario}/{@code tenant} passados ao
 * construtor precisam já ter um ID atribuído. Como ambos usam
 * {@code @GeneratedValue(strategy = GenerationType.UUID)}, o Hibernate
 * gera o UUID em memória no momento do {@code persist()}/{@code save()},
 * sem precisar de round-trip ao banco antes — então basta que
 * {@code usuario}/{@code tenant} já tenham sido salvos (ou lidos do banco)
 * antes de montar este vínculo, nunca construídos "soltos" na mesma
 * instrução. O construtor valida isso explicitamente (falha alto e cedo
 * em vez de deixar o Hibernate falhar de forma mais confusa mais adiante).</p>
 *
 * <p>Tabela global (seção 25) — não é tenant-aware (é ela própria quem
 * define a relação usuário-tenant).</p>
 */
@Entity
@Table(name = "usuario_tenant")
public class UsuarioTenant {

    public enum Role {
        ADMIN,
        COORDENADOR,
        VISUALIZADOR
    }

    public enum Status {
        ATIVO,
        INATIVO
    }

    @EmbeddedId
    private UsuarioTenantId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("usuarioId")
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tenantId")
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected UsuarioTenant() {
        // JPA
    }

    public UsuarioTenant(Usuario usuario, Tenant tenant, Role role, Status status) {
        Objects.requireNonNull(usuario, "usuario não pode ser nulo");
        Objects.requireNonNull(tenant, "tenant não pode ser nulo");
        if (usuario.getId() == null || tenant.getId() == null) {
            throw new IllegalArgumentException(
                    "usuario e tenant precisam já ter um ID atribuído (salvos ou lidos do banco) "
                            + "antes de montar um UsuarioTenant — ver javadoc da classe.");
        }
        this.usuario = usuario;
        this.tenant = tenant;
        this.id = new UsuarioTenantId(usuario.getId(), tenant.getId());
        this.role = role;
        this.status = status;
    }

    public UsuarioTenantId getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
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
