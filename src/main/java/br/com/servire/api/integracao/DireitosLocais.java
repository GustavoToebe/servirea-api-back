package br.com.servire.api.integracao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "direitos_locais")
public class DireitosLocais {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "contratacao_id", nullable = false)
    private UUID contratacaoId;

    @Column(nullable = false)
    private int versao;

    @Column(nullable = false)
    private String situacao;

    @Column(name = "acesso_liberado", nullable = false)
    private boolean acessoLiberado;

    @Column(name = "motivo_bloqueio")
    private String motivoBloqueio;

    @Column(name = "vigente_ate")
    private LocalDate vigenteAte;

    @Column(name = "plano_codigo")
    private String planoCodigo;

    @Column(name = "plano_nome")
    private String planoNome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String limites;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "funcionalidades", columnDefinition = "text[]")
    private String[] funcionalidades = new String[0];

    @Column(name = "confirmado_em", nullable = false)
    private Instant confirmadoEm;

    protected DireitosLocais() {
    }

    public DireitosLocais(UUID tenantId, UUID contratacaoId) {
        this.tenantId = tenantId;
        this.contratacaoId = contratacaoId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getContratacaoId() {
        return contratacaoId;
    }

    public int getVersao() {
        return versao;
    }

    public void setVersao(int versao) {
        this.versao = versao;
    }

    public boolean isAcessoLiberado() {
        return acessoLiberado;
    }

    public void setAcessoLiberado(boolean acessoLiberado) {
        this.acessoLiberado = acessoLiberado;
    }

    public String getSituacao() {
        return situacao;
    }

    public void setSituacao(String situacao) {
        this.situacao = situacao;
    }

    public String getMotivoBloqueio() {
        return motivoBloqueio;
    }

    public void setMotivoBloqueio(String motivoBloqueio) {
        this.motivoBloqueio = motivoBloqueio;
    }

    public LocalDate getVigenteAte() {
        return vigenteAte;
    }

    public void setVigenteAte(LocalDate vigenteAte) {
        this.vigenteAte = vigenteAte;
    }

    public String getPlanoCodigo() {
        return planoCodigo;
    }

    public void setPlanoCodigo(String planoCodigo) {
        this.planoCodigo = planoCodigo;
    }

    public String getPlanoNome() {
        return planoNome;
    }

    public void setPlanoNome(String planoNome) {
        this.planoNome = planoNome;
    }

    public void setLimites(String limites) {
        this.limites = limites;
    }

    public void setFuncionalidades(String[] funcionalidades) {
        this.funcionalidades = funcionalidades == null ? new String[0] : funcionalidades;
    }

    public Instant getConfirmadoEm() {
        return confirmadoEm;
    }

    public void setConfirmadoEm(Instant confirmadoEm) {
        this.confirmadoEm = confirmadoEm;
    }
}
