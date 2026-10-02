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
 * <p>Autenticação própria e MFA pertencem a esta conta global. Senha
 * nullable existe para convites ainda não aceitos. Segredos MFA são
 * cifrados; nunca incluí-los em DTOs de cadastro ou representação textual.</p>
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

    private String telefone;

    @Column(name = "tipo_telefone")
    private String tipoTelefone;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name="mfa_segredo") private String mfaSegredo;
    @Column(name="mfa_pendente") private String mfaPendente;
    @Column(name="mfa_pendente_ate") private Instant mfaPendenteAte;
    @Column(name="mfa_ultimo_passo",nullable=false) private long mfaUltimoPasso=-1;
    @Column(name="credenciais_versao",nullable=false) private long credenciaisVersao;
    public String getMfaSegredo(){return mfaSegredo;}
    public String getMfaPendente(){return mfaPendente;}
    public Instant getMfaPendenteAte(){return mfaPendenteAte;}
    public long getMfaUltimoPasso(){return mfaUltimoPasso;}
    public long getCredenciaisVersao(){return credenciaisVersao;}
    public void invalidarCredenciais(){credenciaisVersao++;}
    public void prepararMfa(String segredo,Instant ate){mfaPendente=segredo;mfaPendenteAte=ate;}
    public void ativarMfa(long passo){mfaSegredo=mfaPendente;mfaPendente=null;mfaPendenteAte=null;mfaUltimoPasso=passo;invalidarCredenciais();}
    public void desativarMfa(){mfaSegredo=null;mfaPendente=null;mfaPendenteAte=null;mfaUltimoPasso=-1;invalidarCredenciais();}
    public void consumirPassoMfa(long passo){mfaUltimoPasso=passo;}

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
