package br.com.servire.api.pessoa;

import br.com.servire.api.voluntario.Voluntario;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Identidade do cadastro. Papel exclusivo ({@link PessoaPapel}) — voluntário
 * ou responsável, nunca os dois. Contato e relação moram nas coleções;
 * o perfil de serviço (escala, foto, funções) fica em {@link Voluntario}
 * 1:1 só quando o papel é {@code VOLUNTARIO}.
 */
@Entity
@Table(name = "pessoa")
public class Pessoa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private PessoaPapel papel;

    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    private String sexo;

    private String cpf;

    private String rg;

    private String cep;

    private String cidade;

    private String uf;

    private String logradouro;

    private String numero;

    private String complemento;

    private String bairro;

    private String observacoes;

    @OneToMany(mappedBy = "pessoa", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("principal DESC, email ASC")
    private List<PessoaEmail> emails = new ArrayList<>();

    @OneToMany(mappedBy = "pessoa", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("principal DESC, numero ASC")
    private List<PessoaTelefone> telefones = new ArrayList<>();

    @OneToMany(mappedBy = "voluntario", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PessoaRelacao> responsaveis = new ArrayList<>();

    @OneToMany(mappedBy = "responsavel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PessoaRelacao> dependentes = new ArrayList<>();

    @OneToOne(mappedBy = "pessoa", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Voluntario voluntario;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Pessoa() {
    }

    public Pessoa(PessoaPapel papel, String nomeCompleto) {
        this.papel = papel;
        this.nomeCompleto = nomeCompleto;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public PessoaPapel getPapel() {
        return papel;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public void setNomeCompleto(String nomeCompleto) {
        this.nomeCompleto = nomeCompleto;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public String getSexo() {
        return sexo;
    }

    public void setSexo(String sexo) {
        this.sexo = sexo;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getRg() {
        return rg;
    }

    public void setRg(String rg) {
        this.rg = rg;
    }

    public String getCep() {
        return cep;
    }

    public void setCep(String cep) {
        this.cep = cep;
    }

    public String getCidade() {
        return cidade;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf;
    }

    public String getLogradouro() {
        return logradouro;
    }

    public void setLogradouro(String logradouro) {
        this.logradouro = logradouro;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getComplemento() {
        return complemento;
    }

    public void setComplemento(String complemento) {
        this.complemento = complemento;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public List<PessoaEmail> getEmails() {
        return emails;
    }

    public List<PessoaTelefone> getTelefones() {
        return telefones;
    }

    public List<PessoaRelacao> getResponsaveis() {
        return responsaveis;
    }

    public List<PessoaRelacao> getDependentes() {
        return dependentes;
    }

    public Voluntario getVoluntario() {
        return voluntario;
    }

    public void setVoluntario(Voluntario voluntario) {
        this.voluntario = voluntario;
        if (voluntario != null) {
            voluntario.setPessoa(this);
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
