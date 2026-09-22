package br.com.servire.api.escala;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Um evento (missa) dentro de uma {@link Escala} — mapeia
 * {@code escala_eventos} (V006 + {@code tenant_id} de V021), Fase 9, seção
 * 9.2/46/109 do plano mestre.
 *
 * <p>O front-end atual apaga e recria todos os eventos de uma escala a
 * cada save (comentário original de V006, "comportamento preservado nesta
 * reconstrução, não corrigido") — {@link EscalaService} preserva esse
 * mesmo comportamento deliberadamente, usando {@code clear()} +
 * reinserção na coleção {@link Escala#getEventos()} (o mesmo padrão já
 * usado para {@code responsaveis} na Fase 6), que o Hibernate traduz em
 * DELETE dos antigos ({@code orphanRemoval=true}) + INSERT dos novos no
 * flush — sem SQL nativo (Native Query Gate, seção 81: nunca bypassar o
 * filtro de tenant do Hibernate).</p>
 */
@Entity
@Table(name = "escala_eventos")
public class EscalaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escala_id", nullable = false)
    private Escala escala;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private LocalTime horario;

    @Column(nullable = false)
    private String celebracao = "Missa";

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("funcao ASC, posicao ASC")
    private List<EscalaVaga> vagas = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected EscalaEvento() {
        // JPA
    }

    public EscalaEvento(LocalDate data, LocalTime horario, String celebracao) {
        this.data = data;
        this.horario = horario;
        this.celebracao = celebracao == null || celebracao.isBlank() ? "Missa" : celebracao;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Escala getEscala() {
        return escala;
    }

    public void setEscala(Escala escala) {
        this.escala = escala;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public LocalTime getHorario() {
        return horario;
    }

    public void setHorario(LocalTime horario) {
        this.horario = horario;
    }

    public String getCelebracao() {
        return celebracao;
    }

    public void setCelebracao(String celebracao) {
        this.celebracao = celebracao;
    }

    public List<EscalaVaga> getVagas() {
        return vagas;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
