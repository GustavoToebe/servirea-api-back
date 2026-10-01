package br.com.servire.api.evento;

import br.com.servire.api.pessoa.Pessoa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Pessoa inscrita num evento. {@code autorizaWhatsapp} é a autorização no momento da inscrição; sem ela
 * nenhuma mensagem sai. Os ids de destinatário apontam para a fila dos comunicados.
 */
@Entity
@Table(name = "evento_inscricao")
public class EventoInscricao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    @Column(name = "autoriza_whatsapp", nullable = false)
    private boolean autorizaWhatsapp;

    @Column(name = "confirmacao_destinatario_id")
    private UUID confirmacaoDestinatarioId;

    @Column(name = "lembrete_destinatario_id")
    private UUID lembreteDestinatarioId;

    @Column(name = "lembrete_enviado_em")
    private Instant lembreteEnviadoEm;

    @Column(name = "confirmacao_email_destinatario_id")
    private UUID confirmacaoEmailDestinatarioId;

    @Column(name = "lembrete_email_destinatario_id")
    private UUID lembreteEmailDestinatarioId;

    @Column(name = "lembrete_email_enviado_em")
    private Instant lembreteEmailEnviadoEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected EventoInscricao() {
    }

    public EventoInscricao(UUID eventoId, Pessoa pessoa, boolean autorizaWhatsapp) {
        this.eventoId = eventoId;
        this.pessoa = pessoa;
        this.autorizaWhatsapp = autorizaWhatsapp;
    }

    public UUID getId() { return id; }
    public UUID getEventoId() { return eventoId; }
    public Pessoa getPessoa() { return pessoa; }
    public boolean isAutorizaWhatsapp() { return autorizaWhatsapp; }
    public UUID getConfirmacaoDestinatarioId() { return confirmacaoDestinatarioId; }
    public void setConfirmacaoDestinatarioId(UUID id) { this.confirmacaoDestinatarioId = id; }
    public UUID getLembreteDestinatarioId() { return lembreteDestinatarioId; }
    public Instant getLembreteEnviadoEm() { return lembreteEnviadoEm; }
    public UUID getConfirmacaoEmailDestinatarioId() { return confirmacaoEmailDestinatarioId; }
    public void setConfirmacaoEmailDestinatarioId(UUID id) { this.confirmacaoEmailDestinatarioId = id; }
    public UUID getLembreteEmailDestinatarioId() { return lembreteEmailDestinatarioId; }
    public Instant getLembreteEmailEnviadoEm() { return lembreteEmailEnviadoEm; }
    public Instant getCreatedAt() { return createdAt; }

    /** Marca o lembrete de WhatsApp como enfileirado. */
    public void lembreteEnfileirado(UUID destinatarioId, Instant quando) {
        this.lembreteDestinatarioId = destinatarioId;
        this.lembreteEnviadoEm = quando;
    }

    /** Marca o lembrete de e-mail como enfileirado. */
    public void lembreteEmailEnfileirado(UUID destinatarioId, Instant quando) {
        this.lembreteEmailDestinatarioId = destinatarioId;
        this.lembreteEmailEnviadoEm = quando;
    }
}
