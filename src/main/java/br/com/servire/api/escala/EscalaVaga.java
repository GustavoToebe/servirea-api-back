package br.com.servire.api.escala;

import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.Voluntario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma vaga (função a preencher) dentro de um {@link EscalaEvento} — mapeia
 * {@code escala_vagas} (V007 + {@code tenant_id} de V021), Fase 9, seção
 * 9.2/109 do plano mestre.
 *
 * <p>{@code funcao} reaproveita o MESMO {@link FuncaoEscala} já usado por
 * {@code voluntarios.funcoes_habilitadas} (Fase 6) — aqui é uma única
 * coluna de enum nativo (não array), então o mapeamento é o padrão simples
 * já confirmado em {@link Voluntario#getTipo()}/{@code Tenant.status}
 * ({@code @Enumerated(STRING)} + {@code @JdbcTypeCode(NAMED_ENUM)}), sem o
 * risco extra do array que a Fase 6 teve.</p>
 *
 * <p>{@code voluntario} é nullable (a vaga pode estar vaga) e
 * {@code ON DELETE SET NULL} no banco (V007/V021) — ou seja, excluir um
 * voluntário nunca apaga a vaga, só a esvazia. As constraints
 * {@code UNIQUE(evento_id, funcao, posicao)} e o índice único parcial
 * {@code uq_voluntario_por_evento} (um voluntário não pode ocupar duas
 * vagas no mesmo evento) são só do banco — não replicadas aqui como
 * {@code @UniqueConstraint} porque {@code ddl-auto: validate} não exige
 * isso para bater (só valida colunas/tipos mapeados), e a validação de
 * negócio equivalente fica em {@link EscalaService}.</p>
 *
 * <p>{@code presenca} foi somado na Fase 11 (controle de faltas, seção
 * 131.5 item 11, migration V024) — mapeia o tipo nativo
 * {@code presenca_vaga}, mesmo padrão de {@code funcao} acima.</p>
 */
@Entity
@Table(name = "escala_vagas")
public class EscalaVaga {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private EscalaEvento evento;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private FuncaoEscala funcao;

    @Column(nullable = false)
    private int posicao = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voluntario_id")
    private Voluntario voluntario;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Presenca presenca = Presenca.PENDENTE;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RespostaParticipacao resposta = RespostaParticipacao.PENDENTE;

    @Column(name = "resposta_em")
    private Instant respostaEm;

    @jakarta.persistence.Version
    @Column(name = "resposta_versao", nullable = false)
    private long respostaVersao;

    public RespostaParticipacao getResposta() { return resposta; }
    public Instant getRespostaEm() { return respostaEm; }
    public long getRespostaVersao() { return respostaVersao; }

    public void responder(RespostaParticipacao resposta, Instant quando) {
        this.resposta = resposta;
        this.respostaEm = quando;
    }

    public void invalidarResposta() {
        resposta = RespostaParticipacao.PENDENTE;
        respostaEm = null;
    }

    protected EscalaVaga() {
        // JPA
    }

    public EscalaVaga(FuncaoEscala funcao, int posicao) {
        this.funcao = funcao;
        this.posicao = posicao;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public EscalaEvento getEvento() {
        return evento;
    }

    public void setEvento(EscalaEvento evento) {
        this.evento = evento;
    }

    public FuncaoEscala getFuncao() {
        return funcao;
    }

    public void setFuncao(FuncaoEscala funcao) {
        this.funcao = funcao;
    }

    public int getPosicao() {
        return posicao;
    }

    public void setPosicao(int posicao) {
        this.posicao = posicao;
    }

    public Voluntario getVoluntario() {
        return voluntario;
    }

    public void setVoluntario(Voluntario voluntario) {
        UUID anterior = this.voluntario == null ? null : this.voluntario.getId();
        UUID proximo = voluntario == null ? null : voluntario.getId();
        if (!java.util.Objects.equals(anterior, proximo)) invalidarResposta();
        this.voluntario = voluntario;
    }

    public Presenca getPresenca() {
        return presenca;
    }

    public void setPresenca(Presenca presenca) {
        this.presenca = presenca;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
