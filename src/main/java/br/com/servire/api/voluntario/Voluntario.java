package br.com.servire.api.voluntario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade tenant-aware mínima usada como vetor do teste crítico de
 * isolamento (seção 78/79/80 do plano mestre) - propositalmente NÃO
 * mapeia todas as colunas da tabela {@code voluntarios} (V003), só o
 * necessário para provar que o mecanismo {@code @TenantId} funciona:
 * {@code id}, {@code tenantId}, {@code nomeCompleto}, {@code ativo}.
 *
 * <p>Colunas restantes (tipo, endereço, funcoes_habilitadas - um array
 * de enum nativo - etc.) ficam de fora de propósito: mapear
 * {@code funcoes_habilitadas} exigiria confirmar o mapeamento de array
 * de enum nativo do Postgres, que é um risco a mais e não é necessário
 * para provar isolamento entre tenants. Entidade completa + controller +
 * service + DTO ficam para a Fase 6 (seção 106).</p>
 *
 * <p>{@code @TenantId} faz o Hibernate popular {@code tenant_id}
 * automaticamente na escrita (a partir do
 * {@link br.com.servire.api.tenant.ServireCurrentTenantIdentifierResolver})
 * e adicionar {@code WHERE tenant_id = ?} automaticamente em toda leitura
 * via este {@code EntityManager}/repositório - por isso o campo não tem
 * setter público: seu valor nunca deve ser definido manualmente pelo
 * código de aplicação.</p>
 */
@Entity
@Table(name = "voluntarios")
public class Voluntario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Voluntario() {
        // JPA
    }

    public Voluntario(String nomeCompleto) {
        this.nomeCompleto = nomeCompleto;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public void setNomeCompleto(String nomeCompleto) {
        this.nomeCompleto = nomeCompleto;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
