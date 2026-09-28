package br.com.servire.api.inscricao;

import br.com.servire.api.pessoa.CondicaoEspecial;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.TipoVoluntario;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rascunho do formulário público até a aprovação. Identidade e contato
 * espelham {@code pessoa}; na aprovação viram pessoa VOLUNTARIO + perfil
 * 1:1 e responsáveis reaproveitados por e-mail principal.
 */
@Entity
@Table(name = "inscricoes")
public class Inscricao {

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

    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    private String sexo;

    private String cpf;

    private String rg;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TipoVoluntario tipo = TipoVoluntario.COROINHA;

    @Column(name = "foto_path")
    private String fotoPath;

    @Column(name = "etapa_catequese")
    private String etapaCatequese;

    @Column(name = "eucaristia_ano")
    private String eucaristiaAno;

    @Column(name = "crisma_ano")
    private String crismaAno;

    private String rua;

    private String numero;

    private String bairro;

    private String cep;

    private String cidade;

    private String uf;

    private String complemento;

    @Enumerated(EnumType.STRING)
    @Column(name = "horario_estudo")
    private Voluntario.HorarioEstudo horarioEstudo;

    private String observacoes;

    @Column(name = "autoriza_whatsapp", nullable = false)
    private boolean autorizaWhatsapp = false;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Enumerated(EnumType.STRING)
    @ColumnTransformer(write = "?::funcao_escala[]")
    @Column(name = "funcoes_habilitadas", nullable = false)
    private FuncaoEscala[] funcoesHabilitadas = new FuncaoEscala[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Enumerated(EnumType.STRING)
    @ColumnTransformer(write = "?::condicao_especial[]")
    @Column(name = "condicoes", nullable = false)
    private CondicaoEspecial[] condicoes = new CondicaoEspecial[0];

    @Column(name = "nivel_suporte_tea")
    private Integer nivelSuporteTea;

    @Column(name = "condicao_outra")
    private String condicaoOutra;

    @Column(name = "cuidados")
    private String cuidados;

    @Column(name = "consentimento_cuidados_em")
    private Instant consentimentoCuidadosEm;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private StatusInscricao status = StatusInscricao.PENDENTE;

    @Column(name = "data_aprovacao")
    private Instant dataAprovacao;

    @Column(name = "aprovado_por")
    private UUID aprovadoPor;

    @Column(name = "voluntario_id")
    private UUID voluntarioId;

    @Column(name = "data_rejeicao")
    private Instant dataRejeicao;

    @Column(name = "rejeitado_por")
    private UUID rejeitadoPor;

    @Column(name = "motivo_rejeicao")
    private String motivoRejeicao;

    @OneToMany(mappedBy = "inscricao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("principal DESC, email ASC")
    private List<InscricaoEmail> emails = new ArrayList<>();

    @OneToMany(mappedBy = "inscricao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("principal DESC, numero ASC")
    private List<InscricaoTelefone> telefones = new ArrayList<>();

    @OneToMany(mappedBy = "inscricao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("principal DESC, nome ASC")
    private List<InscricaoResponsavel> responsaveis = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Inscricao() {
    }

    public Inscricao(String nomeCompleto) {
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

    public TipoVoluntario getTipo() {
        return tipo;
    }

    public void setTipo(TipoVoluntario tipo) {
        this.tipo = tipo;
    }

    public String getFotoPath() {
        return fotoPath;
    }

    public void setFotoPath(String fotoPath) {
        this.fotoPath = fotoPath;
    }

    public String getEtapaCatequese() {
        return etapaCatequese;
    }

    public void setEtapaCatequese(String etapaCatequese) {
        this.etapaCatequese = etapaCatequese;
    }

    public String getEucaristiaAno() {
        return eucaristiaAno;
    }

    public void setEucaristiaAno(String eucaristiaAno) {
        this.eucaristiaAno = eucaristiaAno;
    }

    public String getCrismaAno() {
        return crismaAno;
    }

    public void setCrismaAno(String crismaAno) {
        this.crismaAno = crismaAno;
    }

    public String getRua() {
        return rua;
    }

    public void setRua(String rua) {
        this.rua = rua;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
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

    public String getComplemento() {
        return complemento;
    }

    public void setComplemento(String complemento) {
        this.complemento = complemento;
    }

    public Voluntario.HorarioEstudo getHorarioEstudo() {
        return horarioEstudo;
    }

    public void setHorarioEstudo(Voluntario.HorarioEstudo horarioEstudo) {
        this.horarioEstudo = horarioEstudo;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public boolean isAutorizaWhatsapp() {
        return autorizaWhatsapp;
    }

    public void setAutorizaWhatsapp(boolean autorizaWhatsapp) {
        this.autorizaWhatsapp = autorizaWhatsapp;
    }

    public FuncaoEscala[] getFuncoesHabilitadas() {
        return funcoesHabilitadas;
    }

    public void setFuncoesHabilitadas(FuncaoEscala[] funcoesHabilitadas) {
        this.funcoesHabilitadas = funcoesHabilitadas != null ? funcoesHabilitadas : new FuncaoEscala[0];
    }

    public CondicaoEspecial[] getCondicoes() {
        return condicoes;
    }

    public void setCondicoes(CondicaoEspecial[] condicoes) {
        this.condicoes = condicoes != null ? condicoes : new CondicaoEspecial[0];
    }

    public Integer getNivelSuporteTea() {
        return nivelSuporteTea;
    }

    public void setNivelSuporteTea(Integer nivelSuporteTea) {
        this.nivelSuporteTea = nivelSuporteTea;
    }

    public String getCondicaoOutra() {
        return condicaoOutra;
    }

    public void setCondicaoOutra(String condicaoOutra) {
        this.condicaoOutra = condicaoOutra;
    }

    public String getCuidados() {
        return cuidados;
    }

    public void setCuidados(String cuidados) {
        this.cuidados = cuidados;
    }

    public Instant getConsentimentoCuidadosEm() {
        return consentimentoCuidadosEm;
    }

    public void setConsentimentoCuidadosEm(Instant consentimentoCuidadosEm) {
        this.consentimentoCuidadosEm = consentimentoCuidadosEm;
    }

    public StatusInscricao getStatus() {
        return status;
    }

    public void setStatus(StatusInscricao status) {
        this.status = status;
    }

    public Instant getDataAprovacao() {
        return dataAprovacao;
    }

    public void setDataAprovacao(Instant dataAprovacao) {
        this.dataAprovacao = dataAprovacao;
    }

    public UUID getAprovadoPor() {
        return aprovadoPor;
    }

    public void setAprovadoPor(UUID aprovadoPor) {
        this.aprovadoPor = aprovadoPor;
    }

    public UUID getVoluntarioId() {
        return voluntarioId;
    }

    public void setVoluntarioId(UUID voluntarioId) {
        this.voluntarioId = voluntarioId;
    }

    public Instant getDataRejeicao() {
        return dataRejeicao;
    }

    public void setDataRejeicao(Instant dataRejeicao) {
        this.dataRejeicao = dataRejeicao;
    }

    public UUID getRejeitadoPor() {
        return rejeitadoPor;
    }

    public void setRejeitadoPor(UUID rejeitadoPor) {
        this.rejeitadoPor = rejeitadoPor;
    }

    public String getMotivoRejeicao() {
        return motivoRejeicao;
    }

    public void setMotivoRejeicao(String motivoRejeicao) {
        this.motivoRejeicao = motivoRejeicao;
    }

    public List<InscricaoEmail> getEmails() {
        return emails;
    }

    public List<InscricaoTelefone> getTelefones() {
        return telefones;
    }

    public List<InscricaoResponsavel> getResponsaveis() {
        return responsaveis;
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
}
