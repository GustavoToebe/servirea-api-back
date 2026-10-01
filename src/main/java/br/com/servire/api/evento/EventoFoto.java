package br.com.servire.api.evento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Foto do evento no Storage (bucket privado: só o caminho fica aqui; a URL é assinada na hora). */
@Entity
@Table(name = "evento_foto")
public class EventoFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(nullable = false, length = 300)
    private String caminho;

    @Column(name = "foto_tamanho_bytes")
    private Long fotoTamanhoBytes;

    public Long getFotoTamanhoBytes() { return fotoTamanhoBytes; }
    public void setFotoTamanhoBytes(Long valor) { fotoTamanhoBytes = valor; }

    @Column(nullable = false)
    private boolean capa;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected EventoFoto() {
    }

    public EventoFoto(UUID eventoId, String caminho, boolean capa) {
        this.eventoId = eventoId;
        this.caminho = caminho;
        this.capa = capa;
    }

    public UUID getId() { return id; }
    public UUID getEventoId() { return eventoId; }
    public String getCaminho() { return caminho; }
    public boolean isCapa() { return capa; }
    public void setCapa(boolean capa) { this.capa = capa; }
    public Instant getCreatedAt() { return createdAt; }
}
