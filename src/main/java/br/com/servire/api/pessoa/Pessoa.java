package br.com.servire.api.pessoa;

import br.com.servire.api.voluntario.Voluntario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Identidade do cadastro. Papéis ({@link PessoaPapel}) não são exclusivos:
 * {@code e_voluntario} e {@code e_responsavel} podem coexistir. Contato e
 * relação moram nas coleções; o perfil de serviço fica em {@link Voluntario}
 * 1:1 quando a pessoa é voluntária. Responsável é opcional (adulto/ministro).
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

    @Column(name = "e_voluntario", nullable = false)
    private boolean eVoluntario;

    @Column(name = "e_responsavel", nullable = false)
    private boolean eResponsavel;

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

    /**
     * Só existe quando {@code e_voluntario}. {@code @Fetch(JOIN)} é
     * obrigatório (bug de 24/09/2026): com {@code @MapsId} o Hibernate 7
     * trata o id da própria pessoa como "FK" do lado {@code mappedBy} e,
     * ao inicializar um responsável por select separado (proxy de
     * {@link PessoaRelacao#getResponsavel()}), a ausência da linha em
     * {@code voluntarios} + o filtro do {@code @TenantId} viravam
     * {@code EntityFilterException}. Com LEFT JOIN a ausência é só
     * {@code null}. ({@code @NotFound(IGNORE)} não resolve no lado
     * {@code mappedBy} — testado.)
     */
    @OneToOne(mappedBy = "pessoa", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Fetch(FetchMode.JOIN)
    private Voluntario voluntario;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Pessoa() {
    }

    public Pessoa(PessoaPapel papel, String nomeCompleto) {
        this(papel == null ? Set.of() : Set.of(papel), nomeCompleto);
    }

    public Pessoa(Set<PessoaPapel> papeis, String nomeCompleto) {
        this.nomeCompleto = nomeCompleto;
        setPapeis(papeis);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public boolean isVoluntario() {
        return eVoluntario;
    }

    public boolean isResponsavel() {
        return eResponsavel;
    }

    public Set<PessoaPapel> getPapeis() {
        EnumSet<PessoaPapel> papeis = EnumSet.noneOf(PessoaPapel.class);
        if (eVoluntario) {
            papeis.add(PessoaPapel.VOLUNTARIO);
        }
        if (eResponsavel) {
            papeis.add(PessoaPapel.RESPONSAVEL);
        }
        return papeis;
    }

    public void setPapeis(Set<PessoaPapel> papeis) {
        this.eVoluntario = papeis != null && papeis.contains(PessoaPapel.VOLUNTARIO);
        this.eResponsavel = papeis != null && papeis.contains(PessoaPapel.RESPONSAVEL);
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
