package br.com.servire.api.voluntario;

import br.com.servire.api.pessoa.Pessoa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Perfil de serviço 1:1 com {@link Pessoa} ({@code e_voluntario = true}).
 * O UUID é o da pessoa ({@code @MapsId}) — vagas, disponibilidade e
 * inscrição aprovada continuam apontando para este id.
 */
@Entity
@Table(name = "voluntarios")
public class Voluntario {

    public enum HorarioEstudo {
        MANHA,
        TARDE,
        NOITE
    }

    @Id
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private Pessoa pessoa;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "horario_estudo")
    private HorarioEstudo horarioEstudo;

    @Column(name = "autoriza_whatsapp", nullable = false)
    private boolean autorizaWhatsapp = false;

    @Column(name = "mandato_inicio")
    private LocalDate mandatoInicio;

    @Column(name = "mandato_fim")
    private LocalDate mandatoFim;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Enumerated(EnumType.STRING)
    @ColumnTransformer(write = "?::funcao_escala[]")
    @Column(name = "funcoes_habilitadas", nullable = false)
    private FuncaoEscala[] funcoesHabilitadas = new FuncaoEscala[0];

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public Voluntario() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Pessoa getPessoa() {
        return pessoa;
    }

    public void setPessoa(Pessoa pessoa) {
        this.pessoa = pessoa;
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

    public HorarioEstudo getHorarioEstudo() {
        return horarioEstudo;
    }

    public void setHorarioEstudo(HorarioEstudo horarioEstudo) {
        this.horarioEstudo = horarioEstudo;
    }

    public boolean isAutorizaWhatsapp() {
        return autorizaWhatsapp;
    }

    public void setAutorizaWhatsapp(boolean autorizaWhatsapp) {
        this.autorizaWhatsapp = autorizaWhatsapp;
    }

    public LocalDate getMandatoInicio() {
        return mandatoInicio;
    }

    public void setMandatoInicio(LocalDate mandatoInicio) {
        this.mandatoInicio = mandatoInicio;
    }

    public LocalDate getMandatoFim() {
        return mandatoFim;
    }

    public void setMandatoFim(LocalDate mandatoFim) {
        this.mandatoFim = mandatoFim;
    }

    public FuncaoEscala[] getFuncoesHabilitadas() {
        return funcoesHabilitadas;
    }

    public void setFuncoesHabilitadas(FuncaoEscala[] funcoesHabilitadas) {
        this.funcoesHabilitadas = funcoesHabilitadas != null ? funcoesHabilitadas : new FuncaoEscala[0];
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
