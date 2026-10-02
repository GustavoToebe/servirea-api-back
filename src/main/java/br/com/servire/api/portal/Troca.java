package br.com.servire.api.portal;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.time.*;
import java.util.UUID;
/** Histórico independente da existência da vaga e das pessoas. Nunca reserva ou remove a alocação. */
@Entity @Table(name="escala_troca")
public class Troca {
 public enum Situacao { AGUARDANDO_ACEITE, ACEITA, APROVADA, RECUSADA, CANCELADA, EXPIRADA }
 @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
 @TenantId @Column(name="tenant_id",nullable=false) UUID tenantId;
 @Column(name="escala_id",nullable=false) UUID escalaId;
 @Column(name="vaga_id",nullable=false) UUID vagaId;
 @Column(name="solicitante_id",nullable=false) UUID solicitanteId;
 @Column(name="substituto_id",nullable=false) UUID substitutoId;
 @Column(name="solicitante_usuario_id",nullable=false) UUID solicitanteUsuarioId;
 @Column(name="substituto_usuario_id",nullable=false) UUID substitutoUsuarioId;
 @Column(name="vaga_versao",nullable=false) long vagaVersao;
 @Enumerated(EnumType.STRING) @Column(nullable=false) Situacao situacao;
 @Version long versao;
 @Column(name="criada_em",nullable=false) Instant criadaEm;
 @Column(name="atualizada_em",nullable=false) Instant atualizadaEm;
 @Column(nullable=false) String celebracao;
 @Column(nullable=false) LocalDateTime inicio;
 @Column(nullable=false) String funcao;
}
