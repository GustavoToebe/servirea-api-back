package br.com.servire.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Usuário global do SaaS (Master lógico, seção 25 do plano mestre) — pode
 * estar vinculado a mais de uma paróquia via {@code usuario_tenant}
 * (seção 29). Ver migration V017.
 *
 * <p>Ainda SEM autenticação própria funcionando (isso é Fase 5, seção
 * 105/32-36) — esta entidade só espelha a tabela para não ter que
 * redesenhar o schema depois ("Isso evita refazer autenticação depois",
 * seção 103). {@code senhaHash} é nullable por enquanto pelo mesmo
 * motivo: nada nesta fase escreve ou lê essa coluna ainda.</p>
 *
 * <p>{@code createdAt}/{@code updatedAt} são {@code insertable=false,
 * updatable=false} porque são geridos pelo banco (DEFAULT now() /
 * trigger {@code trg_usuario_updated_at}), não pela aplicação.</p>
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "senha_hash")
    private String senhaHash;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;

    /**
     * Operador do SaaS (backoffice, seção 111). Não tem paróquia no JWT;
     * entra por {@code POST /admin/auth/login}. O ADMIN da paróquia
     * continua sendo só a role em {@code usuario_tenant}.
     */
    @Column(name = "operador_saas", nullable = false)
    private boolean operadorSaas = false;

    private String telefone;

    @Column(name = "tipo_telefone")
    private String tipoTelefone;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Usuario() {
        // JPA
    }

    public Usuario(String email, String nome) {
        this.email = email;
        this.nome = nome;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
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

    public boolean isOperadorSaas() {
        return operadorSaas;
    }

    public void setOperadorSaas(boolean operadorSaas) {
        this.operadorSaas = operadorSaas;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getTipoTelefone() {
        return tipoTelefone;
    }

    public void setTipoTelefone(String tipoTelefone) {
        this.tipoTelefone = tipoTelefone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
