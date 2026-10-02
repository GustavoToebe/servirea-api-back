package br.com.servire.api.calendario;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
/** Global para resolver tenant de um token opaco antes da abertura da sessão de domínio. */
@Entity @Table(name="calendario_assinatura")
public class CalendarioAssinatura {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @Column(name="tenant_id",nullable=false) public UUID tenantId;
 @Column(name="usuario_id",nullable=false) public UUID usuarioId;
 @Column(name="pessoa_id",nullable=false) public UUID pessoaId;
 @Column(name="token_hash",nullable=false,length=64) public String tokenHash;
 @Column(name="expira_em",nullable=false) public Instant expiraEm;
 @Column(name="criado_em",nullable=false) public Instant criadoEm;
}
