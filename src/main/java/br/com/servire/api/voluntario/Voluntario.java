package br.com.servire.api.voluntario;

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
 * Entidade tenant-aware completa da Fase 6 (seção 37/106 do plano
 * mestre), mapeando todas as colunas de {@code voluntarios} (V003) — até
 * a Fase 5 esta classe só mapeava {@code id}/{@code tenantId}/
 * {@code nomeCompleto}/{@code ativo}, o mínimo para provar o mecanismo de
 * isolamento (ver {@code TenantIsolationIntegrationTest}).
 *
 * <p>{@code @TenantId} continua sem setter público — seu valor é sempre
 * derivado do {@link br.com.servire.api.tenant.TenantContext} pelo
 * {@link br.com.servire.api.tenant.ServireCurrentTenantIdentifierResolver},
 * nunca definido manualmente pelo código de aplicação.</p>
 *
 * <p><b>Risco residual a verificar no próximo build real (seção 105 dá o
 * precedente deste padrão de nota):</b> {@code funcoesHabilitadas} mapeia
 * {@code funcao_escala[]}, um array de ENUM nativo do Postgres — o tipo
 * de mapeamento mais arriscado desta fase (por isso tinha ficado de fora
 * da entidade mínima da Fase 4/5, ver javadoc anterior desta classe).
 * Pesquisado em 21/09/2026: combinar {@code @JdbcTypeCode(SqlTypes.ARRAY)}
 * com {@code @JdbcType(PostgreSQLEnumJdbcType.class)} tem um bug
 * conhecido no Hibernate 7.x (`ClassCastException` em
 * {@code getJdbcLiteralFormatter}, reportado contra Hibernate
 * 7.2.1.Final — thread oficial do fórum Hibernate ORM, "PostgreSQL array
 * of enums does not work with @JdbcType(PostgreSQLEnumJdbcType.class)
 * but works with @Enumerated + @ColumnTransformer"). A solução
 * confirmada nessa mesma thread — {@code @JdbcTypeCode(SqlTypes.ARRAY)} +
 * {@code @Enumerated(EnumType.STRING)} + {@code @ColumnTransformer(write
 * = "?::funcao_escala[]")} — é a adotada abaixo. Não foi possível
 * confirmar contra a versão exata deste projeto (Hibernate 7.4.5.Final)
 * rodando de verdade (sem acesso a um Postgres real neste ambiente de
 * pesquisa) — se o próximo {@code mvn clean verify} acusar erro nesta
 * coluna, este é o primeiro lugar a checar.</p>
 */
@Entity
@Table(name = "voluntarios")
public class Voluntario {

    /** Valores de {@code horario_estudo} — coluna {@code text} com CHECK (V003), não um ENUM nativo do Postgres. */
    public enum HorarioEstudo {
        MANHA,
        TARDE,
        NOITE
    }

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

    @Column(nullable = false)
    private boolean ativo = true;

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
    private HorarioEstudo horarioEstudo;

    private String observacoes;

    @Column(name = "autoriza_whatsapp", nullable = false)
    private boolean autorizaWhatsapp = false;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Enumerated(EnumType.STRING)
    @ColumnTransformer(write = "?::funcao_escala[]")
    @Column(name = "funcoes_habilitadas", nullable = false)
    private FuncaoEscala[] funcoesHabilitadas = new FuncaoEscala[0];

    @OneToMany(mappedBy = "voluntario", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("principal DESC, nome ASC")
    private List<Responsavel> responsaveis = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Voluntario() {
        // JPA
    }

    /** Construtor mínimo mantido por compatibilidade com {@code TenantIsolationIntegrationTest} (Fase 4/5). */
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

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
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

    public HorarioEstudo getHorarioEstudo() {
        return horarioEstudo;
    }

    public void setHorarioEstudo(HorarioEstudo horarioEstudo) {
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

    public List<Responsavel> getResponsaveis() {
        return responsaveis;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
