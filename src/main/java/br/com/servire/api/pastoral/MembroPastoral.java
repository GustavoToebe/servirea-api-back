package br.com.servire.api.pastoral;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.util.UUID;
@Entity @Table(name="pastoral_membro")
public class MembroPastoral {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
 @Column(name="equipe_id",nullable=false) public UUID equipeId;
 @Column(name="pessoa_id",nullable=false) public UUID pessoaId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public Papel papel;
 public boolean ativo=true;
 @Version public long versao;
 public enum Papel {MEMBRO,COORDENADOR}
}
