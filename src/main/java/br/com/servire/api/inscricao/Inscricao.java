package br.com.servire.api.inscricao;

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
 * Cadastro pendente vindo do formulário público {@code /public/{slug}/inscricoes}
 * (Fase 8, seção 44/108 do plano mestre) — mapeia {@code inscricoes} (V008
 * + {@code tenant_id} de V021). Espelha praticamente todos os campos de
 * {@link Voluntario} (mesmo formulário, seção 20.3) mais os campos de
 * auditoria de aprovação/rejeição.
 *
 * <p>{@code funcoesHabilitadas} usa o MESMO padrão de mapeamento de array
 * de ENUM nativo do Postgres já confirmado por build real em
 * {@link Voluntario#getFuncoesHabilitadas()} (Fase 6, 22/09/2026) —
 * {@code @JdbcTypeCode(SqlTypes.ARRAY)} + {@code @Enumerated(EnumType.STRING)}
 * + {@code @ColumnTransformer(write = "?::funcao_escala[]")}. Como a
 * coluna e o tipo Postgres são idênticos aos de {@code voluntarios}, o
 * risco aqui é bem menor que o da Fase 6 (mesmo padrão, já provado).</p>
 *
 * <p>{@code aprovadoPor}/{@code rejeitadoPor} são {@code UUID} simples
 * (não {@code @ManyToOne} para {@code Usuario}) — só precisamos gravar
 * "quem", nunca navegar da inscrição para o usuário; a FK que o banco
 * mantém (repontada de {@code auth.users} para {@code usuario} pela V023,
 * ver seu comentário) garante a integridade sem precisar de um
 * relacionamento JPA.</p>
 *
 * <p>As CHECK constraints {@code inscricoes_aprovada_ck}/
 * {@code inscricoes_rejeitada_ck} (V008) exigem que, ao mudar para
 * APROVADA/REJEITADA, os campos de auditoria correspondentes já estejam
 * preenchidos — {@link InscricaoService#aprovar}/{@link InscricaoService#rejeitar}
 * são os únicos lugares que fazem essa transição, sempre setando todos os
 * campos exigidos juntos, na mesma transação.</p>
 */
@Entity
@Table(name = "inscricoes")
public class Inscricao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

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

    private String telefone;

    private String celular;

    private String email;

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
    @OrderBy("principal DESC, nome ASC")
    private List<InscricaoResponsavel> responsaveis = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Inscricao() {
        // JPA
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

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public List<InscricaoResponsavel> getResponsaveis() {
        return responsaveis;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
