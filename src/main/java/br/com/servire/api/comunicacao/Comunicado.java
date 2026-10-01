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

/** Comunicado enviado a pessoas por e-mail ou WhatsApp (PLANO-005, V043). */
@Entity
@Table(name = "comunicado")
public class Comunicado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEnvio canal;

    @Column(name = "layout_id")
    private UUID layoutId;

    @Column(name = "layout_nome", nullable = false, length = 120)
    private String layoutNome;

    @Column(length = 200)
    private String assunto;

    @Enumerated(EnumType.STRING)
    @Column(name = "enviar_para", nullable = false, length = 20)
    private EnviarPara enviarPara;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusComunicado status = StatusComunicado.NA_FILA;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int enviados;

    @Column(nullable = false)
    private int falhas;

    @Column(name = "criado_por")
    private UUID criadoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    protected Comunicado() {
    }

    public Comunicado(TipoEnvio canal, Layout layout, String assunto, EnviarPara enviarPara, UUID criadoPor) {
        this.canal = canal;
        this.layoutId = layout.getId();
        this.layoutNome = layout.getNome();
        this.assunto = assunto;
        this.enviarPara = enviarPara;
        this.criadoPor = criadoPor;
    }

    /** Mensagem do próprio sistema (ex.: confirmação de evento), sem layout: {@code origem} aparece no histórico. */
    Comunicado(TipoEnvio canal, String origem, UUID criadoPor) {
        this.canal = canal;
        this.layoutNome = origem.length() > 120 ? origem.substring(0, 120) : origem;
        this.enviarPara = EnviarPara.PESSOA;
        this.criadoPor = criadoPor;
    }

    public UUID getId() { return id; }
    public TipoEnvio getCanal() { return canal; }
    public UUID getLayoutId() { return layoutId; }
    public String getLayoutNome() { return layoutNome; }
    public String getAssunto() { return assunto; }
    public EnviarPara getEnviarPara() { return enviarPara; }
    public StatusComunicado getStatus() { return status; }
    public int getTotal() { return total; }
    public int getEnviados() { return enviados; }
    public int getFalhas() { return falhas; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConcluidoEm() { return concluidoEm; }

    void setTotal(int total) { this.total = total; }
    void setAssunto(String assunto) { this.assunto = assunto; }

    void marcarEnviando() {
        if (status == StatusComunicado.NA_FILA) status = StatusComunicado.ENVIANDO;
    }

    void contar(int enviados, int falhas) {
        this.enviados = enviados;
        this.falhas = falhas;
    }

    void concluir() {
        status = StatusComunicado.CONCLUIDO;
        concluidoEm = Instant.now();
    }

    void voltarParaFila() {
        status = StatusComunicado.NA_FILA;
        concluidoEm = null;
    }
}
