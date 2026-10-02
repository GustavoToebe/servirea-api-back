package br.com.servire.api.portal;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.time.*;
import java.util.UUID;

/** Pedido não reserva vaga; IDs e dados da celebração preservam o histórico de vagas recriadas. */
@Entity @Table(name="escala_candidatura")
public class Candidatura {
    public enum Situacao { PENDENTE, APROVADA, RECUSADA, DESISTIDA, EXPIRADA }
    @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
    @TenantId @Column(name="tenant_id",nullable=false) UUID tenantId;
    @Column(name="escala_id",nullable=false) UUID escalaId;
    @Column(name="vaga_id",nullable=false) UUID vagaId;
    @Column(name="pessoa_id",nullable=false) UUID pessoaId;
    @Column(name="usuario_id",nullable=false) UUID usuarioId;
    @Column(name="vaga_versao",nullable=false) long vagaVersao;
    @Enumerated(EnumType.STRING) @Column(nullable=false) Situacao situacao;
    @Version long versao;
    @Column(name="criada_em",nullable=false) Instant criadaEm;
    @Column(name="atualizada_em",nullable=false) Instant atualizadaEm;
    @Column(nullable=false) String celebracao;
    @Column(nullable=false) LocalDateTime inicio;
    @Column(nullable=false) String funcao;
}
