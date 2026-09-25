package br.com.servire.api.acesso;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Perfil de uma paróquia. Tabela global de propósito: o filtro JWT lê o
 * perfil fora de transação, no mesmo motivo de {@code usuario_tenant}.
 * {@code tenant_id} é FK comum, não {@code @TenantId}.
 */
@Entity
@Table(name = "perfil")
public class Perfil {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "acesso_total", nullable = false)
    private boolean acessoTotal;

    @Column(nullable = false)
    private boolean sistema;

    @OneToMany(mappedBy = "perfil", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PerfilPermissao> permissoes = new ArrayList<>();

    protected Perfil() {
    }

    public Perfil(UUID tenantId, String nome, boolean acessoTotal, boolean sistema) {
        this.tenantId = tenantId;
        this.nome = nome;
        this.acessoTotal = acessoTotal;
        this.sistema = sistema;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public boolean isAcessoTotal() {
        return acessoTotal;
    }

    public void setAcessoTotal(boolean acessoTotal) {
        this.acessoTotal = acessoTotal;
    }

    public boolean isSistema() {
        return sistema;
    }

    public List<PerfilPermissao> getPermissoes() {
        return permissoes;
    }

    public void substituirPermissoes(List<String> codigos) {
        permissoes.clear();
        for (String codigo : codigos) {
            permissoes.add(new PerfilPermissao(this, codigo));
        }
    }
}
