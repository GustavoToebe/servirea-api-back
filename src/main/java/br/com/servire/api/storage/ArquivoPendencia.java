package br.com.servire.api.storage;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Intenção durável sobre um arquivo no Storage (T13). UPLOAD = enviado antes de a referência estar confirmada no banco;
 * REMOCAO = o arquivo deixou de ser referenciado e precisa sair do bucket. Sobrevive a falha do provedor e a queda da API.
 */
@Entity
@Table(name = "arquivo_pendencia")
public class ArquivoPendencia {
    public enum Tipo { UPLOAD, REMOCAO }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(nullable = false, length = 500)
    public String caminho;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public Tipo tipo;

    @Column(name = "criado_em", nullable = false)
    public Instant criadoEm;

    @Column(nullable = false)
    public int tentativas;

    @Column(name = "proxima_tentativa", nullable = false)
    public Instant proximaTentativa;

    @Column(length = 200)
    public String erro;

    @Column(name = "reservado_por")
    public UUID reservadoPor;

    @Column(name = "reserva_ate")
    public Instant reservaAte;
}
