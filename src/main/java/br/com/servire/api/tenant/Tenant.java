package br.com.servire.api.tenant;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cada linha é uma paróquia (tenant) do SaaS — tabela global do "Master
 * lógico" (seção 25 do plano mestre), raiz da hierarquia multi-tenant
 * (seção 17/27). Ver migration V016.
 *
 * <p>Contato 1:N em {@link TenantEmail}/{@link TenantTelefone} (V030) —
 * tabelas globais, sem {@code @TenantId}.</p>
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

    private String cep;

    private String cidade;

    private String uf;

    private String bairro;

    private String logradouro;

    private String numero;

    private String complemento;

    private String observacoes;

    @Column(name = "ultimo_pagamento_em")
    private Instant ultimoPagamentoEm;

    @Column(name = "vigencia_ate")
    private LocalDate vigenciaAte;

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("principal DESC, email ASC")
    private List<TenantEmail> emails = new ArrayList<>();

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("principal DESC, numero ASC")
    private List<TenantTelefone> telefones = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Tenant() {
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

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
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

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public Instant getUltimoPagamentoEm() {
        return ultimoPagamentoEm;
    }

    public void setUltimoPagamentoEm(Instant ultimoPagamentoEm) {
        this.ultimoPagamentoEm = ultimoPagamentoEm;
    }

    public LocalDate getVigenciaAte() {
        return vigenciaAte;
    }

    public void setVigenciaAte(LocalDate vigenciaAte) {
        this.vigenciaAte = vigenciaAte;
    }

    public List<TenantEmail> getEmails() {
        return emails;
    }

    public List<TenantTelefone> getTelefones() {
        return telefones;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
