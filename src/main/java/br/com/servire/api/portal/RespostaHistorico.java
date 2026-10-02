package br.com.servire.api.portal;

import br.com.servire.api.escala.RespostaParticipacao;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.time.Instant;
import java.util.UUID;

/** Histórico independente da vida da vaga; somente UUIDs, decisão e horário. */
@Entity @Table(name="escala_resposta_historico")
public class RespostaHistorico {
    @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
    @TenantId @Column(name="tenant_id", nullable=false) UUID tenantId;
    @Column(name="escala_id", nullable=false) UUID escalaId;
    @Column(name="vaga_id", nullable=false) UUID vagaId;
    @Column(name="pessoa_id", nullable=false) UUID pessoaId;
    @Column(name="usuario_id", nullable=false) UUID usuarioId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) RespostaParticipacao resposta;
    @Column(name="respondido_em", nullable=false) Instant respondidoEm;
    @Column(nullable=false) long versao;
}
