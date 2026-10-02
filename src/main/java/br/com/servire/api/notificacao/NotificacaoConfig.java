package br.com.servire.api.notificacao;

import br.com.servire.api.comunicacao.TipoEnvio;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

/** Gatilho automático por origem e canal (F05/F13). Nasce desligado; ligar é decisão da paróquia. */
@Entity
@Table(name = "notificacao_config")
public class NotificacaoConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public OrigemNotificacao origem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public TipoEnvio canal;

    @Column(nullable = false)
    public boolean ativo;

    @Version
    public long versao;
}
