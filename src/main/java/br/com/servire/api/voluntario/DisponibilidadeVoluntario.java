package br.com.servire.api.voluntario;

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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Disponibilidade do voluntário (Fase 11 do plano mestre — item 12 da
 * seção 122, detalhado na seção 131.5): dias/horários em que ele PODE
 * servir, pensado como filtro adicional para o picker de candidatos
 * (seção 49) — integração com o picker ainda não feita nesta rodada, ver
 * javadoc de {@code EscalaController} e "Próximos passos" do README.
 *
 * <p>Mapeia {@code disponibilidade_voluntario} (V025). Exatamente um entre
 * {@code diaSemana} (disponibilidade recorrente, ex. "toda quarta") e
 * {@code data} (exceção pontual, ex. "só dia 25/12") é preenchido — regra
 * garantida em dois níveis: a CHECK {@code disponibilidade_voluntario_dia_xor_data}
 * no banco, e a validação explícita no construtor abaixo (mesmo padrão de
 * "falha alto e cedo" já usado em {@link br.com.servire.api.auth.UsuarioTenant}).</p>
 *
 * <p>{@code diaSemana} usa o {@link DayOfWeek} do próprio JDK — não um ENUM
 * nativo do Postgres criado por este projeto (mapeado só como
 * {@code @Enumerated(STRING)} para uma coluna {@code text} com CHECK,
 * V025) — mesmo raciocínio já documentado em
 * {@link Voluntario.HorarioEstudo}: não é um vocabulário que este domínio
 * define, então não ganha um tipo Postgres próprio.</p>
 *
 * <p>Recurso simples, sem necessidade de edição — o cliente remove e
 * recria em vez de atualizar um registro existente, por isso não há
 * setters aqui (só o necessário para o Hibernate hidratar a entidade).</p>
 */
@Entity
@Table(name = "disponibilidade_voluntario")
public class DisponibilidadeVoluntario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voluntario_id", nullable = false)
    private Voluntario voluntario;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana")
    private DayOfWeek diaSemana;

    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Periodo periodo;

    private String observacao;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected DisponibilidadeVoluntario() {
        // JPA
    }

    public DisponibilidadeVoluntario(Voluntario voluntario, DayOfWeek diaSemana, LocalDate data, Periodo periodo, String observacao) {
        Objects.requireNonNull(voluntario, "voluntario não pode ser nulo");
        Objects.requireNonNull(periodo, "periodo não pode ser nulo");
        if ((diaSemana == null) == (data == null)) {
            throw new IllegalArgumentException(
                    "Exatamente um entre diaSemana e data deve ser informado (nunca os dois, nunca nenhum) — "
                            + "ver javadoc da classe.");
        }
        this.voluntario = voluntario;
        this.diaSemana = diaSemana;
        this.data = data;
        this.periodo = periodo;
        this.observacao = observacao;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Voluntario getVoluntario() {
        return voluntario;
    }

    public DayOfWeek getDiaSemana() {
        return diaSemana;
    }

    public LocalDate getData() {
        return data;
    }

    public Periodo getPeriodo() {
        return periodo;
    }

    public String getObservacao() {
        return observacao;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
