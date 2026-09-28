package br.com.servire.api.escala;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * "Respondeu sem restrição" no mês (V045): separa quem avisou que pode em todas as datas de quem
 * não respondeu. Uma linha por voluntário e mês (UNIQUE tenant, voluntário, ano, mês).
 */
@Entity
@Table(name = "resposta_indisponibilidade")
public class RespostaIndisponibilidade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "voluntario_id", nullable = false)
    private UUID voluntarioId;

    @Column(nullable = false)
    private int ano;

    @Column(nullable = false)
    private int mes;

    @Column(name = "sem_restricao", nullable = false)
    private boolean semRestricao;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected RespostaIndisponibilidade() {
    }

    public RespostaIndisponibilidade(UUID voluntarioId, int ano, int mes, boolean semRestricao) {
        this.voluntarioId = voluntarioId;
        this.ano = ano;
        this.mes = mes;
        this.semRestricao = semRestricao;
    }

    public UUID getVoluntarioId() { return voluntarioId; }
    public boolean isSemRestricao() { return semRestricao; }
}
