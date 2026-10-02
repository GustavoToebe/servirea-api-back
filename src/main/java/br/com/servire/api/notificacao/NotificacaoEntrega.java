package br.com.servire.api.notificacao;

import br.com.servire.api.comunicacao.TipoEnvio;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Uma notificação enfileirada. A chave (origem, referência, versão, canal) impede repetir o mesmo aviso. */
@Entity
@Table(name = "notificacao_entrega")
public class NotificacaoEntrega {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public OrigemNotificacao origem;

    @Column(name = "referencia_id", nullable = false)
    public UUID referenciaId;

    @Column(name = "referencia_versao", nullable = false)
    public long referenciaVersao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public TipoEnvio canal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    public Gatilho gatilho;

    @Column(nullable = false)
    public int total;

    @Column(nullable = false)
    public int ignorados;

    @Column(name = "comunicado_id")
    public UUID comunicadoId;

    @Column(name = "criado_por")
    public UUID criadoPor;

    @Column(name = "criado_em", nullable = false)
    public Instant criadoEm;

    public enum Gatilho { MANUAL, AUTOMATICO }
}
