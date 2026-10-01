package br.com.servire.api.comunicacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Uma mensagem da fila: quem recebe, para onde e o conteúdo já renderizado. */
@Entity
@Table(name = "comunicado_destinatario")
public class ComunicadoDestinatario {

    static final int MAX_TENTATIVAS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "comunicado_id", nullable = false)
    private UUID comunicadoId;

    @Column(name = "pessoa_id")
    private UUID pessoaId;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false, length = 200)
    private String destino;

    @Column(nullable = false, columnDefinition = "text")
    private String conteudo;

    @Column(length = 200)
    private String assunto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEnvio status = StatusEnvio.PENDENTE;

    @Column(nullable = false)
    private int tentativas;

    @Column(length = 500)
    private String erro;

    @Column(name = "enviado_em")
    private Instant enviadoEm;

    @Column(name="proxima_tentativa",nullable=false) private Instant proximaTentativa=Instant.EPOCH;
    @Column(name="reservado_por") private UUID reservadoPor;
    @Column(name="reserva_ate") private Instant reservaAte;
    boolean pronto(Instant agora) { return status==StatusEnvio.PENDENTE && !proximaTentativa.isAfter(agora) && (reservaAte==null || !reservaAte.isAfter(agora)); }
    void reservar(UUID dono,Instant ate) { reservadoPor=dono; reservaAte=ate; }
    boolean pertenceA(UUID dono,Instant agora) { return dono.equals(reservadoPor) && reservaAte!=null && reservaAte.isAfter(agora); }
    void liberar() { reservadoPor=null; reservaAte=null; }
    void reagendar(Instant quando) { proximaTentativa=quando; }
    void falhaDefinitiva(String motivo) { status=StatusEnvio.FALHA; erro=motivo; }
    @Column(name="cota_competencia") private java.time.LocalDate cotaCompetencia;
    public java.time.LocalDate getCotaCompetencia() { return cotaCompetencia; }
    /** Reserva durável da unidade: falha/crash/reenvio não apagam esta competência. */
    public void contabilizarCota(java.time.LocalDate competencia) {
        if (cotaCompetencia == null) cotaCompetencia = competencia;
    }
    void aguardarCota(Instant quando) {
        proximaTentativa = quando;
        erro = "Aguardando disponibilidade da cota mensal de envios.";
    }
    protected ComunicadoDestinatario() {
    }

    public ComunicadoDestinatario(UUID comunicadoId, UUID pessoaId, String nome, String destino, String assunto, String conteudo) {
        this.comunicadoId = comunicadoId;
        this.pessoaId = pessoaId;
        this.nome = nome;
        this.destino = destino;
        this.assunto = assunto;
        this.conteudo = conteudo;
    }

    public UUID getId() { return id; }
    public UUID getComunicadoId() { return comunicadoId; }
    public UUID getPessoaId() { return pessoaId; }
    public String getNome() { return nome; }
    public String getDestino() { return destino; }
    public String getConteudo() { return conteudo; }
    public String getAssunto() { return assunto; }
    public StatusEnvio getStatus() { return status; }
    public int getTentativas() { return tentativas; }
    public String getErro() { return erro; }
    public Instant getEnviadoEm() { return enviadoEm; }

    void marcarEnviado() {
        status = StatusEnvio.ENVIADO;
        enviadoEm = Instant.now();
        erro = null;
    }

    void registrarFalha(String mensagem) {
        tentativas++;
        erro = mensagem == null ? "Falha no envio." : mensagem.length() > 500 ? mensagem.substring(0, 500) : mensagem;
        if (tentativas >= MAX_TENTATIVAS) status = StatusEnvio.FALHA;
    }

    void voltarParaFila() {
        status = StatusEnvio.PENDENTE;
        tentativas = 0;
        proximaTentativa=Instant.EPOCH;
        erro = null;
    }
}
